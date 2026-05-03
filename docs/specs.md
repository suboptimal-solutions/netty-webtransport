# Specs reading guide

The vendored specs live in [`../specs/`](../specs/). This doc tells you which
spec to open for a given implementation task.

## Glossary

| Term | Meaning |
| --- | --- |
| **Session** | A WebTransport connection between a client and a server. Multiple sessions can share one HTTP/3 connection. Identified by the HTTP/3 request-stream ID of the originating CONNECT. |
| **Stream** | A QUIC stream associated with a session. Bidirectional or unidirectional. WebTransport adds a small header (`WT_STREAM` for bidi, a stream-type varint for uni) before the application payload. |
| **Datagram** | An unreliable WebTransport message carried by a QUIC DATAGRAM frame (RFC 9221), wrapped in an HTTP Datagram (RFC 9297) with a context-id that identifies the session. |
| **Capsule** | A typed message on the HTTP/3 CONNECT stream itself, used for control signaling: flow control, drain, close. Format: type (varint) + length (varint) + payload. |
| **Context-ID** | The varint at the start of every HTTP Datagram, demultiplexing flows on a single HTTP/3 connection. For WebTransport this is the **quarter stream ID** (session ID divided by 4). |
| **Quarter Stream ID** | `session_id / 4`, exploiting that QUIC client-bidi stream IDs are always multiples of 4. Saves bytes in every datagram header. |
| **WT_STREAM** | The HTTP/3 frame type that opens a WebTransport bidirectional stream. Carries the session ID; everything after is application payload. |
| **Extended CONNECT** | RFC 9220 — the HTTP/3 CONNECT method with a `:protocol` pseudo-header. WebTransport uses `:protocol = webtransport`. |
| **Application error code** | A 32-bit code an application can attach to a session or stream close. Mapped to a 64-bit HTTP/3 error code on the wire. |
| **HTTP/3 SETTINGS** | The capability negotiation frame on the HTTP/3 control stream. WebTransport uses `SETTINGS_ENABLE_CONNECT_PROTOCOL`, `SETTINGS_H3_DATAGRAM`, `SETTINGS_WT_MAX_SESSIONS`, etc. |

## Reading guide by task

### Implementing the capsule codec

Read **RFC 9297** (`rfc9297.txt`). Sections:

- §3.1 — Capsule Protocol wire format (type, length, value)
- §3.3 — DATAGRAM capsule (used as the fallback datagram path)
- §4 — Capsule type registry semantics

Then **`draft-ietf-webtrans-http3-15`** (`draft-ietf-webtrans-http3-15.txt`)
for the WebTransport-specific capsule types (DRAIN, CLOSE, MAX_STREAMS, etc.).

### Implementing session lifecycle

Read **`draft-ietf-webtrans-http3-15`**:

- §3 — Session establishment (extended CONNECT, the `:protocol = webtransport`
  request, server response)
- §5 — SETTINGS negotiation: `SETTINGS_ENABLE_CONNECT_PROTOCOL`,
  `SETTINGS_H3_DATAGRAM`, `SETTINGS_WT_MAX_SESSIONS`
- §6 — Session termination: DRAIN_SESSION, CLOSE_SESSION capsules

Cross-reference **RFC 9220** (`rfc9220.txt`) for the extended-CONNECT mechanism
itself.

### Implementing stream multiplexing

Read **`draft-ietf-webtrans-http3-15`**:

- §4.1 — Bidirectional streams: WT_STREAM frame format, session-id encoding
- §4.2 — Unidirectional streams: stream-type varint, session-id

Stream creation flow control is in §4.3.

### Implementing datagram demux

Read in this order:

1. **RFC 9221** (`rfc9221.txt`) — QUIC DATAGRAM frame format. §3 covers
   `max_datagram_frame_size` transport-parameter negotiation; §5 covers
   send/receive semantics.
2. **RFC 9297** §2 — HTTP Datagram frame format on top of QUIC DATAGRAM,
   including the context-id varint.
3. **`draft-ietf-webtrans-http3-15`** §4.4 — quarter-stream-id encoding for
   WebTransport datagrams.

### Implementing flow control

Read **`draft-ietf-webtrans-http3-15`** §7. Flow control is per-session and
travels via capsules:

- WT_MAX_STREAMS — limit concurrent streams in a session
- WT_MAX_DATA — limit bytes in a session
- WT_STREAMS_BLOCKED / WT_DATA_BLOCKED — signaling

Browser-originated traffic is bound by these; the server must honor and
enforce them.

### Implementing error codes

Read **`draft-ietf-webtrans-http3-15`** §8. The application's 32-bit error
code is mapped to a 64-bit HTTP/3 error code via the formula in §8.1
(`first + value`, where `first = 0x52e4a40fa8db` and reserved HTTP/3 codepoints
are skipped).

### Browser-side reference

Read **`w3c-webtransport.html`** for the JavaScript API surface that browsers
expose. This is not a wire-protocol document — it tells you what semantics the
server protocol must support so a `new WebTransport(...)` call works. Useful
sections (search the rendered HTML):

- `WebTransport` constructor — sets `:method`, `:protocol`, headers
- `incomingBidirectionalStreams`, `incomingUnidirectionalStreams` — server-pushed
  streams the application can read
- `datagrams.readable` / `datagrams.writable` — how datagrams surface
- `closed`, `close()` — how session lifecycle maps to JS promises and methods

## Spec-to-code map

When the codec exists, [wire-format.md](wire-format.md) will list each codec
class with the exact spec section it implements. Until then, this doc is the
entrypoint.
