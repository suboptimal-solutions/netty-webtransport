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
| **WT_STREAM** | Signal value `0x41` at the start of a WebTransport bidirectional stream, followed by the session ID, then the application payload. Registered as an HTTP/3 frame type to reserve the codepoint, but it has no length field and is not a real HTTP/3 frame; the HTTP/3 codec cannot parse it. |
| **Extended CONNECT** | RFC 9220 — the HTTP/3 CONNECT method with a `:protocol` pseudo-header. WebTransport uses `:protocol = webtransport`. |
| **Application error code** | A 32-bit code an application can attach to a session or stream close. Mapped to a 64-bit HTTP/3 error code on the wire. |
| **HTTP/3 SETTINGS** | The capability negotiation frame on the HTTP/3 control stream. WebTransport uses `SETTINGS_ENABLE_CONNECT_PROTOCOL`, `SETTINGS_H3_DATAGRAM`, `SETTINGS_WT_MAX_SESSIONS`, etc. |

## Reading guide by task

### Implementing the capsule codec

Read **RFC 9297** (`rfc9297.txt`). Sections:

- §3.2 — The Capsule Protocol (wire format: type + length + value)
- §3.5 — The DATAGRAM Capsule (used as the fallback datagram path)
- §5.4 — IANA capsule-type registry

Then **`draft-ietf-webtrans-http3-15`** for the WebTransport-specific capsule
types (`WT_DRAIN_SESSION`, `WT_CLOSE_SESSION`, `WT_MAX_STREAMS`, `WT_MAX_DATA`,
`WT_STREAMS_BLOCKED`, `WT_DATA_BLOCKED`).

### Implementing session lifecycle

Read **`draft-ietf-webtrans-http3-15`**:

- §3.1 — Establishing a WebTransport-capable HTTP/3 connection (SETTINGS:
  `SETTINGS_ENABLE_CONNECT_PROTOCOL`, `SETTINGS_H3_DATAGRAM`,
  `SETTINGS_WT_MAX_SESSIONS`)
- §3.2 — Creating a new session (extended CONNECT with
  `:protocol = webtransport-h3`)
- §6 — Session termination (`WT_DRAIN_SESSION`, `WT_CLOSE_SESSION`)

Cross-reference **RFC 9220** for the extended-CONNECT mechanism itself.

### Implementing stream multiplexing

Read **`draft-ietf-webtrans-http3-15`**:

- §4.2 — Unidirectional streams: stream type `0x54`, then session ID, then
  application data
- §4.3 — Bidirectional streams: signal value `0x41` (registered as the
  `WT_STREAM` HTTP/3 frame type but without a length field), then session
  ID, then application data
- §4.4 — Resetting data streams (also where the application error-code
  mapping lives)

Stream-creation flow control is in §5 (Flow Control).

### Implementing datagram demux

Read in this order:

1. **RFC 9221** — QUIC DATAGRAM frame format. §3 covers
   `max_datagram_frame_size` transport-parameter negotiation; §5 covers
   send/receive semantics.
2. **RFC 9297** §2 — HTTP Datagram frame format on top of QUIC DATAGRAM,
   including the context-id varint.
3. **`draft-ietf-webtrans-http3-15`** §4.5 — datagram framing for
   WebTransport, with the quarter-stream-id encoding of the context-id.

### Implementing flow control

Read **`draft-ietf-webtrans-http3-15`** §5. Flow control is per-session and
travels via capsules (the wire format of the capsules themselves is in §5.6):

- `WT_MAX_STREAMS` — limit concurrent streams in a session
- `WT_MAX_DATA` — limit bytes in a session
- `WT_STREAMS_BLOCKED` / `WT_DATA_BLOCKED` — signaling

Browser-originated traffic is bound by these; the server must honor and
enforce them. Initial limits are also negotiated in SETTINGS (see §5.5).

### Implementing error codes

Read **`draft-ietf-webtrans-http3-15`** §4.4. The application's 32-bit error
code is mapped to a 64-bit HTTP/3 error code via the formula
`first + n + floor(n / 0x1e)` where `first = 0x52e4a40fa8db`; reserved HTTP/3
codepoints (those of form `0x1f * N + 0x21`) are skipped.

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
