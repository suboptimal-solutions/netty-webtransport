# CLAUDE.md

Guidance for AI / human sessions working on this repository. Read this first.

## What this project is

`netty-codec-webtransport` — a Java library implementing WebTransport over HTTP/3
on top of Netty 4.2.x. Pre-1.0; the codec primitives, server handlers, and
public API are in place. A Java client and end-to-end browser interop are still
to come.

## Spec target (May 2026)

Implementing `draft-ietf-webtrans-http3-15` plus RFC 9220 / 9221 / 9297. Exact
bytes, URLs, and sha256 sums are pinned in [specs/README.md](specs/README.md).
**When the spec changes, update both the vendored file and `specs/README.md` in
the same commit.**

## Module map

- [`netty-codec-webtransport/`](netty-codec-webtransport/) — the codec. Frames,
  capsules, server handlers, session API. Tests in `src/test/java`.
- [`netty-codec-webtransport-example/`](netty-codec-webtransport-example/) —
  runnable demos. Currently hosts an `EchoServer` that exercises the new API
  (session channel, per-stream child channels, datagram frames).
- [`netty-codec-webtransport-tests/`](netty-codec-webtransport-tests/) —
  browser interop tests driven by Playwright Java + Chromium. Skipped by
  default; activate with `mvn -P integration verify`. Six round-trip cases
  (bidi/uni/datagram, server-initiated bidi, clean close with code+reason,
  drain) are green against current Chromium. Wire-level gotchas the bring-up
  exposed — multi-draft SETTINGS, draft-02 response-header echo, Netty's
  silent non-standard-SETTINGS drop, double `Http3FrameCodec` install when
  using `Http3RequestStreamInitializer` — are catalogued in
  [`docs/browser-interop.md`](docs/browser-interop.md). **Read that first
  when an interop test starts failing.**

## Architecture in one paragraph

The shape mirrors Netty's HTTP/2-multiplex idiom (and matches HTTP/3's
parent-child channel model). Add a single `WebTransportServerProtocolHandler`
to a `QuicChannel` pipeline; configure it with three initializers
(`WebTransportSessionInitializer`, `WebTransportStreamInitializer`,
`WebTransportUniStreamInitializer`). Each accepted CONNECT becomes the
**session channel** (a `QuicStreamChannel`); each peer-initiated WebTransport
stream becomes a **child stream channel** (a `QuicStreamChannel`) with its own
pipeline; datagrams are `channelRead` events of type
`WebTransportDatagramFrame` on the session channel. Session lifecycle fires as
Netty user events: `WebTransportSessionEvent.Established` / `Draining` /
`Closed`. Outbound stream creation goes through
`WebTransportStreamChannelBootstrap` (mirrors `Http2StreamChannelBootstrap`).

The full pipeline diagram and rationale live in
[`docs/architecture.md` §1](docs/architecture.md#1-overview).

## Where things live

- Public API surface (handlers, initializers, frames, events, session) →
  `io.suboptimal.netty.webtransport.*` in the codec module. The package
  Javadoc on `package-info.java` is the entry point with a runnable example.
- Codec primitives — `VarintCodec`, `Capsule` (sealed), `CapsuleCodec`,
  `WebTransportProtocol` constants → same package. **These are stable; the
  re-think only changed the shell on top of them.**
- Internal handlers, session implementation, registry →
  `io.suboptimal.netty.webtransport.internal.*`.
- Example handlers (`EchoServer`, `EchoSessionHandler`, `EchoStreamHandler`)
  → `io.suboptimal.netty.webtransport.example` in the example module.
- Vendored specs → [`specs/`](specs/), one file per spec. Read with `less`,
  not a browser; they are the source of truth.
- Design notes → [`docs/architecture.md`](docs/architecture.md). Read before
  proposing any new handler or buffer-handling change.
- Spec-to-class map → [`docs/wire-format.md`](docs/wire-format.md).

## Three hard rules

1. **Don't copy bytes — slice them.** `ByteBuf.slice()` /
   `retainedSlice()` / `readSlice(int)` over `getBytes(...)`,
   `toString()`, or any path that allocates a `byte[]`. Details in
   [docs/architecture.md §2](docs/architecture.md#2-zero-copy-data-path).
2. **Don't block the event loop.** Codec / handler code runs on a Netty event
   loop. Virtual threads are forbidden there; allowed only in application code
   that consumes the session API.
3. **Cite the spec section.** Every codec class and non-trivial handler change
   should reference the exact section of the relevant `specs/*.txt` (e.g.
   `draft-ietf-webtrans-http3-15 §4.2`). Code reviews check this.

## Architectural rules to keep

- **Per-stream channels, not flat frames.** Each WebTransport stream is its
  own `QuicStreamChannel` with its own pipeline. Do **not** introduce a flat
  "stream-data frame on the session channel" surface — it loses per-stream
  backpressure, half-close, and `AUTO_READ`. The hisano implementation took
  the flat path; we deliberately did not.
- **Lifecycle is a user event, not a callback.** Session and stream lifecycle
  is signalled with sealed `WebTransportSessionEvent` / `WebTransportStreamEvent`
  records via `ctx.fireUserEventTriggered(...)`. Do not reintroduce a callback
  abstract class — it inverts Netty's reactive model.
- **Datagrams are pipeline messages on the session channel.** Inbound
  datagrams arrive as `WebTransportDatagramFrame` `channelRead` events;
  outbound writes of the same type are intercepted by the internal session
  outbound handler and forwarded as varint-prefixed datagrams to the parent
  `QuicChannel`. Never pass `ByteBuf` directly to the user as "the datagram".
- **Codec layer is sealed.** `VarintCodec`, `Capsule`, `CapsuleCodec`,
  `WebTransportProtocol` are stable. Don't refactor them when changing the
  pipeline; if you genuinely need a new capsule type, add it to the sealed
  hierarchy and update the wire-format spec citations.

## Build and format

`mvn spotless:apply && mvn -B verify`. Full rationale, JDK / Maven version
requirements, and the no-wrapper decision live in [README.md](README.md) and
[docs/project-layout.md](docs/project-layout.md).

## What's next

See [docs/roadmap.md](docs/roadmap.md). Phases 1-5, 7, and 8 are implemented.
Phase 6 (real flow-control enforcement — capsules are parsed and stored on
`DefaultWebTransportSession` but limits aren't yet enforced on outbound
streams/data) is the next concrete task. Phase 9 (Java client) is still
untouched.

If a browser interop test starts failing, the diagnostic playbook is in
[docs/browser-interop.md](docs/browser-interop.md#debugging-a-future-regression).
Quick path: rerun headed with DevTools open and inspect
`chrome://net-internals/#events` for the actual rejection reason:

```sh
mvn -B -P integration -Dpw.headed=true -Dpw.devtools=true -pl netty-codec-webtransport-tests test
```

## What NOT to do

- Don't add per-file Apache 2.0 headers — see
  [docs/project-layout.md](docs/project-layout.md#licensing).
- Don't depend on incubator Netty artifacts — see
  [docs/netty-stack.md](docs/netty-stack.md#superseded-artifacts-do-not-depend-on).
- Don't write documentation files Claude wasn't asked for.
- Don't put session/stream/datagram surfaces on a parallel callback API.
  Pipeline-native is the contract.
