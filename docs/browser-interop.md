# Browser interop notes

What it actually takes to make `new WebTransport(...)` work against this server
in shipping Chromium. Every section below is something we hit while bringing
up Phase 8 ([roadmap §8](roadmap.md#phase-8--browser-interop)); the fixes are
in tree, this doc exists so the next person who hits a regression knows where
to look.

## TL;DR — what browsers expect today

| Aspect | Spec target (draft-15) | Chromium 147 (May 2026) |
| --- | --- | --- |
| `:protocol` upgrade token | `webtransport-h3` | **`webtransport`** |
| `SETTINGS_*` codepoint advertising support | `SETTINGS_WT_ENABLED = 0x2c7cf000` (boolean) | **`SETTINGS_WEBTRANS_DRAFT00 = 0x2b603742`** (draft-02, on by default), or `SETTINGS_WEBTRANS_MAX_SESSIONS_DRAFT07 = 0xc671706a` (draft-07, **feature-flag gated** behind `kEnableWebTransportDraft07`) |
| Cert-trust path | `serverCertificateHashes` JS option | Same — but enforces ECDSA-P256, validity ≤ 14 days, valid SAN; ALL cert validation runs in Chromium, no automatic CA fallback |
| Required CONNECT response header | (none in draft-15) | **`Sec-Webtransport-Http3-Draft: draft02`** must be echoed when client offered `Sec-Webtransport-Http3-Draft02: 1` |

Source for the Chromium constants: QUICHE
[`http_constants.h`](https://github.com/google/quiche/blob/main/quiche/quic/core/http/http_constants.h)
and `LocallySupportedWebTransportVersions()` in
[`net/quic/dedicated_web_transport_http3_client.cc`](https://chromium.googlesource.com/chromium/src/+/main/net/quic/dedicated_web_transport_http3_client.cc).
QUICHE supports `kDraft02` and `kDraft07`; Chromium gates `kDraft07` behind a
runtime feature flag, so out of the box only draft-02 will negotiate.

The bottom line: **a server that only implements draft-15 cannot interop with
any current browser.** This is acknowledged in draft-15 §7.1 ("Negotiating the
Draft Version") which explicitly allows a server to advertise multiple
SETTINGS codepoints simultaneously, one per supported draft. We do exactly
that — see
[`WebTransportServerProtocolHandler.buildSettings`](../netty-codec-webtransport/src/main/java/io/suboptimal/netty/webtransport/WebTransportServerProtocolHandler.java)
— and we accept both `webtransport-h3` and `webtransport` on extended CONNECT
in
[`WebTransportConnectHandler`](../netty-codec-webtransport/src/main/java/io/suboptimal/netty/webtransport/internal/WebTransportConnectHandler.java).

## Multi-version interop strategy

For each new browser-side WebTransport draft Chromium ships:

1. Add a constant for the new SETTINGS codepoint to
   [`WebTransportProtocol`](../netty-codec-webtransport/src/main/java/io/suboptimal/netty/webtransport/WebTransportProtocol.java)
   (the existing `SETTINGS_WT_ENABLED_DRAFT02` / `SETTINGS_WT_MAX_SESSIONS_DRAFT07`
   are the templates).
2. Put it in the SETTINGS frame in `buildSettings()` alongside the existing
   codepoints — the spec says servers MAY advertise as many as they support;
   the client picks the highest both ends know.
3. If the new draft renames the upgrade token, add it to
   `WebTransportProtocol.UPGRADE_TOKEN_*` and `OR` it into the validity check
   in `WebTransportConnectHandler.channelRead`.
4. If the new draft introduces a new request-response handshake header (like
   draft-02's `Sec-Webtransport-Http3-Draft02` / `Sec-Webtransport-Http3-Draft`),
   add the echo logic next to `HEADER_DRAFT02_OFFER` in `WebTransportConnectHandler`.
5. Add a row to the table at the top of this doc.

## Specific issues we hit during Phase 8

These are the bugs that produced the `WebTransportError: Opening handshake
failed.` symptom in the test harness. Each one had a different root cause and
a different fix. Together they are an interop checklist: if any of them
regresses, the test suite fails.

### 1. Netty's default SETTINGS validator silently drops non-standard codepoints

**Symptom:** `ERR_METHOD_NOT_SUPPORTED` from Chromium. SETTINGS frame on the
wire only contained the QPACK and `SETTINGS_ENABLE_CONNECT_PROTOCOL` /
`SETTINGS_H3_DATAGRAM` entries; every WebTransport-specific codepoint we tried
to put with `Http3Settings.put(id, value)` was missing.

**Diagnosis:** `Http3Settings.defaultSettings()` returns an instance whose
`NonStandardHttp3SettingsValidator` is `(id, value) -> false`. `put()` checks
the validator before storing and returns `null` on rejection — silently. So
every `settings.put(SETTINGS_WT_ENABLED, 1L)` looked successful but never
made it onto the wire.

**Fix:** Construct the settings with a permissive validator
(`ACCEPT_NON_STANDARD_SETTINGS = (id, value) -> true`) and pass the same
validator to `Http3ServerConnectionHandler` so inbound non-standard SETTINGS
from the peer aren't dropped either. See
[`WebTransportServerProtocolHandler`](../netty-codec-webtransport/src/main/java/io/suboptimal/netty/webtransport/WebTransportServerProtocolHandler.java).

### 2. Drift between draft-15 and what Chromium actually negotiates

**Symptom:** SETTINGS frame goes out correctly (containing
`SETTINGS_WT_ENABLED = 0x2c7cf000`), but Chromium still reports
`ERR_METHOD_NOT_SUPPORTED`.

**Diagnosis:** Chromium recognises `0x2b603742` (draft-02) and `0xc671706a`
(draft-07) only — not draft-15's `0x2c7cf000`. Without one of those, QUICHE's
`SupportsWebTransport()` returns false and Chromium refuses to send the
extended CONNECT.

**Fix:** Send all three codepoints. Even when targeting draft-15 internally,
we advertise draft-02 and draft-07 so a current browser sees a token it
recognises. The implementation comment in `buildSettings()` records this
rationale.

### 3. Wrong `:protocol` token

**Symptom:** Server logs `404 Not Found` on incoming CONNECT, browser reports
`Opening handshake failed`.

**Diagnosis:** Draft-15 §3.2 says `:protocol = webtransport-h3`. Drafts 02–07
say `:protocol = webtransport`. Chromium follows the older form.

**Fix:** `WebTransportConnectHandler` accepts either token. Both constants are
in
[`WebTransportProtocol`](../netty-codec-webtransport/src/main/java/io/suboptimal/netty/webtransport/WebTransportProtocol.java)
(`UPGRADE_TOKEN`, `UPGRADE_TOKEN_DRAFT07`).

### 4. Missing draft-02 response header echo

**Symptom:** SETTINGS handshake works, CONNECT accepted (200 sent), but
`wt.ready` never resolves on the JS side and the connection hits the QUIC
idle timeout (4 s).

**Diagnosis:** Draft-02 §3.3 negotiates the wire version via paired headers:
the client offers each draft it supports as `Sec-Webtransport-Http3-Draft<NN>: 1`,
and the server **MUST** confirm the chosen draft with a single
`Sec-Webtransport-Http3-Draft: draftNN` header on the 200 response. Without
the echo, draft-02 Chromium considers the version unnegotiated and silently
drops the session.

**Fix:** `WebTransportConnectHandler.sendOk` reads the offer headers and
echoes the selected draft. See `HEADER_DRAFT02_OFFER` /
`HEADER_DRAFT_SELECTED` / `DRAFT_VALUE_DRAFT02` constants there.

### 5. `Http3RequestStreamInitializer` double-installs the HTTP/3 codec

**Symptom:** First CONNECT works, then on the next inbound frame the pipeline
throws

```
java.lang.ClassCastException:
  DefaultHttp3HeadersFrame cannot be cast to ByteBuf
  at Http3FrameCodec.channelRead(Http3FrameCodec.java:132)
```

**Diagnosis:** `Http3ServerConnectionHandler.initBidirectionalStream` already
adds `Http3FrameCodec` + the encode/decode-state validators +
`Http3RequestStreamValidationHandler` to every accepted bidi stream **before**
it invokes the user-supplied `requestStreamHandler`. If that handler is itself
an `Http3RequestStreamInitializer`, its `initChannel()` adds **another** copy
of the same four handlers. The second `Http3FrameCodec` then receives
already-decoded frames from the first one and tries to `(ByteBuf) msg`-cast
them.

**Fix:** Use a plain `ChannelInitializer<QuicStreamChannel>` instead of
`Http3RequestStreamInitializer` as the `requestStreamHandler` parameter. The
initializer should add only WebTransport-specific handlers (the bidi prefix
discriminator + the CONNECT handler); Netty has already installed the HTTP/3
machinery. See the comment block in
`WebTransportServerProtocolHandler.handlerAdded`.

### 6. Cross-channel promise on outbound datagrams

**Symptom:** Datagram echo write fails with

```
IllegalArgumentException:
  promise.channel does not match: [QuicStreamAddress{streamId=0}]
  (expected: QuicConnectionAddress{...})
```

**Diagnosis:** `WebTransportSessionDatagramOutboundHandler` is on the session
*stream* channel; the actual datagram has to be sent on the *parent* QUIC
connection channel. The outbound `ChannelPromise` belongs to the stream
channel, but `parentChannel().writeAndFlush(buf, promise)` requires a promise
allocated on the parent. Netty rejects the foreign promise.

**Fix:** Bridge with a listener — `parentChannel().writeAndFlush(buf)` returns
a fresh future on the parent; on success/failure forward the result to the
caller's stream-channel promise. See the `addListener` block in
[`WebTransportSessionDatagramOutboundHandler`](../netty-codec-webtransport/src/main/java/io/suboptimal/netty/webtransport/internal/WebTransportSessionDatagramOutboundHandler.java).

### 7. Inbound datagrams re-entering the HTTP/3 codec

**Symptom:** Inbound datagram crashes with

```
ClassCastException:
  WebTransportDatagramFrame cannot be cast to ByteBuf
  at Http3FrameCodec.channelRead(Http3FrameCodec.java:132)
```

**Diagnosis:** `WebTransportDatagramRouter` was firing the decoded
`WebTransportDatagramFrame` via `session.sessionChannel().pipeline().fireChannelRead(frame)`,
which starts at the *head* of the session pipeline — and the head is
`Http3FrameCodec`, which only knows ByteBufs and HTTP/3 frame primitives.

**Fix:** Resolve the `wt-session-out` handler context first and call
`fireChannelRead` from there, bypassing the HTTP/3 codec and delivering the
frame directly to user handlers. See
[`WebTransportDatagramRouter`](../netty-codec-webtransport/src/main/java/io/suboptimal/netty/webtransport/internal/WebTransportDatagramRouter.java).

### 8. Server-initiated reply racing session teardown

**Symptom:** Peer-initiated unidirectional stream test hangs; the JS waits
for an incoming uni reply that never arrives. Server logs show the inbound
payload arriving correctly, but the outbound reply fails with
`ClosedChannelException` because the QUIC connection is already gone.

**Diagnosis:** Specific to draft-02. After the JS calls
`writer.close()` on the outbound uni stream, Chromium also half-closes the
session (CONNECT) stream. Our `WebTransportSessionHandler.channelInputClosed`
treats that as a session shutdown and tears the session down. If the
test fixture's `UniEcho` was waiting for `channelInactive` on the uni stream
to send its reply (the original Netty-idiomatic pattern), the session is gone
by the time the reply tries to open a server-initiated stream.

**Fix:** In the test fixture, send the reply as soon as the *peer's* write
side finishes (`ChannelInputShutdownEvent`), not on full channel inactivity.
This is the right pattern in general for a "read all then reply" handler on a
half-closeable stream; the Netty docs suggest it for HTTP/2 too. See
`UniEcho.userEventTriggered` in
[`InteropEchoServer`](../netty-codec-webtransport-tests/src/test/java/io/suboptimal/netty/webtransport/tests/InteropEchoServer.java).

## Cert path: why `serverCertificateHashes`, not the SPKI Chromium flag

Two ways to make Chromium trust a self-signed cert:

1. `--ignore-certificate-errors-spki-list=base64(sha256(SPKI))` — a Chromium
   command-line flag that bypasses **all** cert verification for keys whose
   SubjectPublicKeyInfo hashes to the supplied value. This is what the
   [hisano/netty-codec-webtransport](https://github.com/hisano/netty-codec-webtransport)
   tests use.
2. `serverCertificateHashes: [{ algorithm: 'sha-256', value: <DER cert hash> }]`
   on the JS `WebTransport(url, options)` constructor. Defined in the W3C
   WebTransport spec; Chromium implements it via
   `WebTransportFingerprintProofVerifier`.

We use **option 2**. It runs the full WebTransport-specific cert validation
path (algorithm whitelist, ≤14 day validity, hash match) instead of disabling
verification, so a passing test demonstrates that the server's certificate
*and* the JS-spec cert-trust mechanism are both correct. The flag approach
would skip all of that, and a server that gets the certificate format wrong
would still appear to work in tests but fail real users.

The cert constraints are encoded in
[`InteropEchoServer.run()`](../netty-codec-webtransport-tests/src/test/java/io/suboptimal/netty/webtransport/tests/InteropEchoServer.java):
ECDSA P-256, 13-day validity (under the 14-day cap), `localhost` as both CN
and the cert's owner. If a future Netty release changes how
`SelfSignedCertificate` populates SAN extensions, that's a likely place to
investigate first.

## Debugging a future regression

The `Opening handshake failed` JS error is generic — the actual reason lives
elsewhere:

1. **Run with `-Dpw.headed=true -Dpw.devtools=true`**:
   ```sh
   mvn -B -P integration -Dpw.headed=true -Dpw.devtools=true \
       -pl netty-codec-webtransport-tests test
   ```
   On failure the harness opens `chrome://net-internals/#events` and
   `chrome://net-internals/#quic` automatically and pauses for inspection
   (`-Dpw.holdOpen` controls the duration). Filter `#events` by URL for the
   actual rejection string.
2. **Check what SETTINGS go on the wire.** Add a one-shot diagnostic
   to `WebTransportServerProtocolHandler.handlerAdded` printing the
   `Http3SettingsFrame` contents. If a WebTransport codepoint is missing,
   the validator is dropping it (issue #1).
3. **Check the CONNECT request headers we receive.** Log
   `headersFrame.headers()` in `WebTransportConnectHandler.channelRead`. If
   `:protocol` is something we don't accept, add the new value (issue #3).
   If there's a draft-version offer header we don't echo, add the echo
   (issue #4).
4. **Check the QUICHE source** for what Chromium 147+ negotiates — the
   `LocallySupportedWebTransportVersions` override and
   `http_constants.h` are the two definitive references (issue #2).
5. **`ClassCastException` in `Http3FrameCodec.channelRead`** is almost always
   the duplicate-codec problem (issue #5) or a frame escaping the HTTP/3 layer
   (issue #7).

## Reference

- Test suite that proves this works:
  [`netty-codec-webtransport-tests`](../netty-codec-webtransport-tests/) —
  6 cases (bidi/uni/datagram round-trips, server-initiated bidi, clean close
  with code+reason, drain).
- Spec section that authorises multi-version SETTINGS:
  [`draft-ietf-webtrans-http3-15.txt` §7.1](../specs/draft-ietf-webtrans-http3-15.txt).
- Phase plan: [roadmap.md §8](roadmap.md#phase-8--browser-interop).
