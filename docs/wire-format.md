# Wire format → spec map

Each public class and each internal handler in
`io.suboptimal.netty.webtransport.*` paired with the spec section(s) it
implements. Reviewers use this to check that the implementation matches what
it claims to.

The vendored specs live in [`../specs/`](../specs/).

## Codec primitives

| Class | Spec |
| --- | --- |
| `VarintCodec` | [`rfc9000.txt` §16](../specs/) (vendored via Netty's HTTP/3 module's transitive deps; the RFC is referenced rather than re-vendored). |
| `Capsule` (sealed interface) | [`rfc9297.txt` §3.2](../specs/rfc9297.txt) — wire format type/length/value. Member types per `draft-ietf-webtrans-http3-15` §5.6 and §6. |
| `Capsule.CloseSession` | [`draft-ietf-webtrans-http3-15.txt` §6](../specs/draft-ietf-webtrans-http3-15.txt) — `CLOSE_WEBTRANSPORT_SESSION`. |
| `Capsule.DrainSession` | [`draft-ietf-webtrans-http3-15.txt` §6](../specs/draft-ietf-webtrans-http3-15.txt) — `DRAIN_WEBTRANSPORT_SESSION`. |
| `Capsule.MaxStreamsBidi` / `MaxStreamsUni` | [`draft-ietf-webtrans-http3-15.txt` §5.6](../specs/draft-ietf-webtrans-http3-15.txt) — `WT_MAX_STREAMS`. |
| `Capsule.MaxData` | [`draft-ietf-webtrans-http3-15.txt` §5.6](../specs/draft-ietf-webtrans-http3-15.txt) — `WT_MAX_DATA`. |
| `Capsule.StreamsBlockedBidi` / `StreamsBlockedUni` | [`draft-ietf-webtrans-http3-15.txt` §5.6](../specs/draft-ietf-webtrans-http3-15.txt) — `WT_STREAMS_BLOCKED`. |
| `Capsule.DataBlocked` | [`draft-ietf-webtrans-http3-15.txt` §5.6](../specs/draft-ietf-webtrans-http3-15.txt) — `WT_DATA_BLOCKED`. |
| `Capsule.Unknown` | Catch-all for unknown capsule types (RFC 9297 §3.2 forward-compat rule). |
| `CapsuleCodec` | [`rfc9297.txt` §3.2](../specs/rfc9297.txt) — stateful decoder over a `CompositeByteBuf`; handles partial frames inside HTTP/3 DATA. |
| `WebTransportProtocol` | [`draft-ietf-webtrans-http3-15.txt` §9](../specs/draft-ietf-webtrans-http3-15.txt) — constants (SETTINGS keys, frame types, stream types, error codes, capsule type IDs, the WT-to-HTTP error-code mapping in §4.4). |

## Public handler / API surface

| Class | Spec |
| --- | --- |
| `WebTransportServerProtocolHandler` | [`draft-ietf-webtrans-http3-15.txt` §3.1](../specs/draft-ietf-webtrans-http3-15.txt) — advertises `SETTINGS_ENABLE_CONNECT_PROTOCOL`, `SETTINGS_H3_DATAGRAM`, `SETTINGS_WT_ENABLED`, optional `WT_INITIAL_MAX_STREAMS_*` / `WT_INITIAL_MAX_DATA`. Wires the HTTP/3 codec, the per-stream prefix handlers, and the datagram router. |
| `WebTransportSession` (interface) | [`draft-ietf-webtrans-http3-15.txt` §3.2](../specs/draft-ietf-webtrans-http3-15.txt). |
| `WebTransportSessionInitializer` | Initializer for the CONNECT request stream (= "session channel"). |
| `WebTransportStreamInitializer` | Initializer for peer-initiated WebTransport bidirectional streams. [`draft-15` §4.2](../specs/draft-ietf-webtrans-http3-15.txt). |
| `WebTransportUniStreamInitializer` | Initializer for peer-initiated WebTransport unidirectional streams. [`draft-15` §4.3](../specs/draft-ietf-webtrans-http3-15.txt). |
| `WebTransportStreamChannelBootstrap` | Outbound stream creation. Writes `WT_STREAM` (0x41) for bidi or `WT_UNI_STREAM_TYPE` (0x54) for uni, then session ID. [`draft-15` §4.1-4.3](../specs/draft-ietf-webtrans-http3-15.txt). |
| `WebTransportSessionEvent` (sealed: `Established`, `Draining`, `Closed`) | Lifecycle user events. [`draft-15` §3](../specs/draft-ietf-webtrans-http3-15.txt) (establishment), [`§6`](../specs/draft-ietf-webtrans-http3-15.txt) (termination). |
| `WebTransportStreamEvent` (sealed: `Opened`, `RemoteReset`) | Stream user events. [`draft-15` §4.2-4.3](../specs/draft-ietf-webtrans-http3-15.txt) (open), [`§4.6`](../specs/draft-ietf-webtrans-http3-15.txt) (reset). |
| `WebTransportFrame` (sealed marker) / `WebTransportDatagramFrame` | [`rfc9297.txt` §2.1](../specs/rfc9297.txt) (HTTP Datagram framing) + [`draft-15` §4.5](../specs/draft-ietf-webtrans-http3-15.txt) (quarter-stream-id context-id). |

## Internal handlers (implementation detail; cited for reviewers)

| Class | Spec |
| --- | --- |
| `internal.SessionRegistry` | Map `session_id → session`. Used by the prefix handlers and the datagram router for O(1) lookup. |
| `internal.DefaultWebTransportSession` | Implements the `WebTransportSession` interface. Owns the `CapsuleCodec` and the flow-control state knobs. [`draft-15` §3.2 + §5](../specs/draft-ietf-webtrans-http3-15.txt). |
| `internal.WebTransportConnectHandler` | Validates `:method = CONNECT` + `:protocol = webtransport-h3`, sends 200 / 404, registers session. [`rfc9220.txt`](../specs/rfc9220.txt) (extended CONNECT) + [`draft-15` §3.2](../specs/draft-ietf-webtrans-http3-15.txt). |
| `internal.WebTransportSessionHandler` | Steady-state inbound handler on the session channel. Decodes capsules from `Http3DataFrame`, applies flow-control state to the session, fires `WebTransportSessionEvent.Closed` / `Draining`. [`rfc9297.txt` §3.2](../specs/rfc9297.txt) + [`draft-15` §5.6, §6](../specs/draft-ietf-webtrans-http3-15.txt). |
| `internal.WebTransportSessionDatagramOutboundHandler` | Outbound interceptor on the session channel. Converts `WebTransportDatagramFrame` writes into varint-prefixed datagrams on the parent QuicChannel. [`rfc9297.txt` §2.1](../specs/rfc9297.txt) + [`draft-15` §4.5](../specs/draft-ietf-webtrans-http3-15.txt). |
| `internal.WebTransportBidiStreamPrefixHandler` | First-byte discriminator on every accepted bidi request stream. If the leading varint is `WT_STREAM` (0x41), reads session ID, removes the HTTP/3 handlers, runs the user's bidi stream initializer; otherwise leaves the stream for HTTP/3 to handle. [`draft-15` §4.2](../specs/draft-ietf-webtrans-http3-15.txt). |
| `internal.WebTransportUniStreamPrefixHandler` | Reads the session-ID varint at the start of a peer-initiated WebTransport uni stream (the `0x54` stream type byte was already consumed by the HTTP/3 layer). [`draft-15` §4.3](../specs/draft-ietf-webtrans-http3-15.txt). |
| `internal.WebTransportDatagramRouter` | Sits on the parent QuicChannel pipeline; reads the quarter-stream-id varint, looks up the session, fires a `WebTransportDatagramFrame` into the matching session channel's pipeline. [`rfc9297.txt` §2.1](../specs/rfc9297.txt) + [`draft-15` §4.5](../specs/draft-ietf-webtrans-http3-15.txt). |

## What is not yet implemented

- **Java client.** `WebTransportClientProtocolHandler` is the symmetric class
  that would belong here once Phase 9 of the [roadmap](roadmap.md) lands.
- **Flow-control enforcement.** Capsules `WT_MAX_STREAMS` / `WT_MAX_DATA` /
  `WT_STREAMS_BLOCKED` / `WT_DATA_BLOCKED` are decoded and the limits are
  stored on the session, but the implementation does not yet block outbound
  stream creation or data writes against them. Phase 6.
- **Resetting a stream with a WebTransport application error code** (draft-15
  §4.6) is not yet plumbed; `WebTransportStreamEvent.RemoteReset` is declared
  but the prefix handlers don't currently observe RESET_STREAM frames at the
  WebTransport layer.

These gaps are explicit so reviewers don't mistake silence for compliance.
