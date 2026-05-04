# Roadmap

Phased implementation plan. **Server-first** — the priority "client" is the
browser via the W3C WebTransport JS API, so the work for it is server-side
wire-format compliance plus interop testing, not a Java client. A Java client
implementation is the **last** protocol task.

All work lands in [`netty-codec-webtransport`](../netty-codec-webtransport/)
unless noted. Each phase corresponds to a minor version bump (`0.1.0`,
`0.2.0`, ...).

## Phase 1 — Varint and frame primitives

Zero-allocation varint encoder / decoder that operates directly on
`ByteBuf`. Frame primitives shared by HTTP/3 and WebTransport (frame type +
length + payload boilerplate). Property-based tests against the QUIC varint
test vectors.

Spec refs: `draft-ietf-webtrans-http3-15` §3 (frame format), inherited from
QUIC RFC 9000 §16.

## Phase 2 — Capsule codec

Encoder and decoder for the Capsule Protocol (RFC 9297). Type registry as a
sealed interface hierarchy with one record per capsule type. Reuse the
varint primitives from Phase 1.

Spec refs: RFC 9297 §3 (Capsule Protocol) + §5 (registry), plus the
WebTransport-specific capsule types in `draft-ietf-webtrans-http3-15` §6
(`WT_DRAIN_SESSION`, `WT_CLOSE_SESSION`) and §5.6 (`WT_MAX_STREAMS`,
`WT_MAX_DATA`, `WT_STREAMS_BLOCKED`, `WT_DATA_BLOCKED`).

## Phase 3 — Session API and lifecycle (server-side)

`WebTransportSession` interface. Session registry. Session lifecycle:
opening, draining, closing. Hooks for incoming streams and datagrams. No
network I/O yet — driven by `EmbeddedChannel` in tests.

Spec refs: `draft-ietf-webtrans-http3-15` §3 (establishment) + §6
(termination), W3C `w3c-webtransport.html` for the semantics the API must
support.

## Phase 4 — Server handler

`WebTransportServerHandler` plugged into the HTTP/3 pipeline. Handles the
extended-CONNECT request (`:protocol = webtransport`), advertises
`SETTINGS_ENABLE_CONNECT_PROTOCOL`, `SETTINGS_H3_DATAGRAM`,
`SETTINGS_WT_MAX_SESSIONS`. Demuxes incoming bidi/uni streams to sessions
via WT_STREAM frame inspection.

Spec refs: RFC 9220 (extended CONNECT), `draft-ietf-webtrans-http3-15` §3.1
(SETTINGS), §4.2-4.3 (stream framing).

## Phase 5 — Datagram context-id demux on server

QUIC DATAGRAM → HTTP Datagram → WebTransport datagram, demuxed by quarter
stream ID. Zero-copy slice passes the payload to the session unchanged. See
[architecture.md §6](architecture.md#6-datagram-fast-path).

Spec refs: RFC 9221 (QUIC datagram), RFC 9297 §2 (HTTP datagram framing),
`draft-ietf-webtrans-http3-15` §4.5 (datagrams + quarter-stream-id).

## Phase 6 — Flow-control capsules

`WT_MAX_STREAMS`, `WT_MAX_DATA`, `WT_STREAMS_BLOCKED`, `WT_DATA_BLOCKED`.
Enforcement on incoming streams; emission to keep up with browser-side
limits. The browser uses these aggressively, so this phase is a prerequisite
for Phase 8 interop.

Spec refs: `draft-ietf-webtrans-http3-15` §5 (Flow Control), with the
capsule wire formats in §5.6.

## Phase 7 — First server-side echo demo

Lands in [`netty-codec-webtransport-example`](../netty-codec-webtransport-example/):
`EchoServer` accepts a session, echoes every bidi/uni stream payload, mirrors
every datagram. Self-signed cert in dev, ALPN `h3`. Documented run command in
the example module's README.

This is what we point a browser at in Phase 8.

## Phase 8 — Browser interop

Bring up `EchoServer`, connect from Chrome:

```js
const wt = new WebTransport('https://localhost:4433/echo', {
    serverCertificateHashes: [{ algorithm: 'sha-256', value: certSha256 }]
});
await wt.ready;
const writer = (await wt.createBidirectionalStream()).writable.getWriter();
await writer.write(new TextEncoder().encode('hello'));
```

Iterate to compliance against `draft-ietf-webtrans-http3-15`. This is the
**practical conformance bar**; pass it and we have a working WebTransport
server. Failures uncovered here drive bug-fix work in Phases 1-7.

The harness for this phase lives in
[`netty-codec-webtransport-tests`](../netty-codec-webtransport-tests/) — a
dedicated module that drives Chromium via Playwright Java against an
in-process server fixture. Six cases are wired:

1. Bidirectional stream echo round-trip.
2. Unidirectional stream + server-initiated reply.
3. Datagram round-trip.
4. Server-initiated bidirectional stream.
5. Clean session close with application error code + reason.
6. `DRAIN_WEBTRANSPORT_SESSION` capsule observed by the JS `wt.draining`
   promise.

Run the suite:

```sh
mvn -B verify -P integration                                    # headless
mvn -B verify -P integration -Dpw.headed=true                   # visible browser window
mvn -B verify -P integration -Dpw.headed=true -Dpw.devtools=true   # + DevTools open
mvn -B verify -P integration -Dpw.headed=true -Dpw.slowmo=500      # 500 ms slow-mo
```

CI runs the integration job in parallel with the unit-test job in
[`.github/workflows/ci.yml`](../.github/workflows/ci.yml).

**Status:** all six tests green. Bring-up turned up eight concrete wire-level
bugs ranging from a silent Netty SETTINGS-validator drop to a Chromium-only
draft-02 response-header echo requirement; root causes and fixes are
catalogued in [browser-interop.md](browser-interop.md). The cert-trust path
is the W3C `serverCertificateHashes` JS option (no Chromium-flag bypasses).

## Phase 9 — Java client handler

`WebTransportClientHandler`, symmetric to the server. Reuses the codec layer
from Phases 1-2. Public API mirrors the server API where it can; the mental
model is "two endpoints, same protocol".

This phase exists for service-to-service WebTransport (Java-on-Java), not for
browser interop, which is already handled.

## Phase 10 — Hardening, perf benchmarks, 1.0.0

JMH benchmarks for the varint codec, capsule encoder/decoder, datagram
demux. Profiler runs to verify zero-allocation hot paths. Property-based
fuzz tests against adversarial frames. Documentation pass.

Cut `1.0.0`. Begin a stability commitment for the public API.

## Status

Phases 1-5, 7, and 8 implemented. Phase 6 (flow-control enforcement) is
stubbed but not enforced. Phase 9 (Java client) is untouched.

The public API matches Netty's HTTP/2-multiplex idiom: each WebTransport
stream is its own `QuicStreamChannel` with an independent pipeline, datagrams
arrive as `WebTransportDatagramFrame` events on the session channel, and
session lifecycle fires as Netty user events
(`WebTransportSessionEvent.Established` / `Draining` / `Closed`).

The browser-interop suite (Phase 8) is green against Chromium 147 with
multi-draft SETTINGS negotiation per spec §7.1; see
[browser-interop.md](browser-interop.md) for the wire-level details and the
list of bugs the bring-up exposed.

Up next: Phase 6 (real flow-control enforcement), then Phase 9 (Java client).
