# Architecture

Design rules every handler, codec class, and helper in
`io.suboptimal.netty.webtransport.*` must follow. These rules are not aspirational
— they are the contract for code review.

The library is **fast and reliable, with zero-copy on data-intensive paths**. That
constraint shapes every decision below.

## 1. Overview

WebTransport sits on top of Netty's HTTP/3 codec, which sits on top of Netty's QUIC
codec. The pipeline (server side):

```
UDP datagrams
    ↓
QuicServerCodecBuilder pipeline  (io.netty:netty-codec-quic + native quiche)
    ↓
Http3ServerConnectionHandler  (io.netty:netty-codec-http3)
    ↓                                     ↓
HTTP/3 control stream            HTTP/3 request streams (one per CONNECT)
                                          ↓
                            WebTransportServerHandler  (us)
                                          ↓
                            WebTransportSession  (us)  ↔  application
```

The `:protocol = webtransport` extended-CONNECT request opens a long-lived stream
that anchors a session. Subsequent client-initiated streams (uni- and bidi-) are
mapped to that session by their session ID. Datagrams are demuxed by the
"quarter stream ID" context-id from RFC 9297. The capsule protocol carries flow
control and lifecycle signals on the CONNECT stream itself.

Client side mirrors the server. The Java client is the last protocol task in
the [roadmap](roadmap.md); the priority client is the browser via the W3C
WebTransport JS API.

## 2. Zero-copy data path

All data-plane code uses `io.netty.buffer.ByteBuf` directly. No `byte[]`, no
`String`, no `ByteBuffer.allocate(...)` on the hot path. The rules:

- **Slice, don't copy.** Splitting a buffer into header / body parts uses
  `ByteBuf.readSlice(int)` or `ByteBuf.retainedSlice(...)`. Both share the
  underlying memory; only the reader/writer indices differ.
- **Compose, don't concatenate.** Assembling a capsule header + body uses
  `Unpooled.wrappedBuffer(headerBuf, bodyBuf)` or `CompositeByteBuf
  .addComponents(true, ...)`. Writing the body into the header buffer with
  `writeBytes(...)` is a copy and is forbidden on data-intensive paths.
- **Direct, pooled buffers.** `PooledByteBufAllocator.DEFAULT` is the default
  allocator. Direct buffers are preferred for I/O paths since they avoid an
  extra copy through the JVM heap on socket writes.
- **No materialization.** `ByteBuf.toString(Charset)`, `ByteBuf.array()`,
  `ByteBuf.getBytes(...)` allocate a fresh `byte[]` and are forbidden on the
  hot path. Reviewers reject PRs that introduce these calls without a written
  justification.
- **Constants for header field names.** Pseudo-headers and protocol tokens
  (`:method`, `:protocol`, `webtransport-h3`, `origin`) are declared as
  `AsciiString` constants once and reused. `String` materialization is
  forbidden on the hot path.
- **Zero-allocation varint codec.** WebTransport uses QUIC variable-length
  integers (RFC 9000 §16) heavily. The encoder/decoder reads and writes
  directly against `ByteBuf` indices; it does not allocate.
- **Cross-event-loop transfers require explicit retain.** A `ByteBuf`
  handed off to a different event loop must be retained by the sender so the
  refcount cannot drop to zero under the receiver. Use `retain()` if both
  sides will share reader/writer indices, or `retainedDuplicate()` /
  `retainedSlice()` if the receiver needs its own indices. Sharing a buffer
  whose indices are mutated by another thread without giving the receiver its
  own view is a bug.

## 3. Threading model

- **One `EventLoop` per QUIC connection.** The Netty channel for a `QuicChannel`
  binds to a single event loop for its entire lifetime. Every handler in the
  pipeline runs on that loop.
- **No blocking on the event loop.** No I/O, no synchronous DNS, no `Thread.sleep`,
  no `synchronized` blocks held across calls. Anything that could block goes to
  an executor.
- **Virtual threads are forbidden on the event loop.** Loom virtual threads pin
  to their carrier when blocked on `synchronized` or native code, and break
  Netty's non-blocking model. Virtual threads are permitted **only** in
  application code that consumes the session API — for example, an application
  handler that reads a stream as a `BlockingQueue<ByteBuf>` from a virtual
  thread is fine.
- **Per-thread caches use `FastThreadLocal`.** Netty event-loop threads are
  `FastThreadLocalThread`, so `FastThreadLocal<T>` is faster than `ThreadLocal<T>`.
  Use it for per-thread varint scratch buffers, decoder state, etc.
- **`Recycler` for high-frequency small objects.** Decoded frame headers and
  capsule descriptors that are allocated per-frame should consider `io.netty
  .util.Recycler`. Caveat: the JIT's escape analysis can scalar-replace
  short-lived objects whose allocations don't escape, making them effectively
  free; benchmark before adopting `Recycler`.
- **Backpressure propagates.** When a `Channel`'s writability flips,
  `channelWritabilityChanged` fires. The session API surfaces this so the
  application can throttle its producer rather than buffering unbounded data
  in the codec.

## 4. Reference-counting discipline

Netty `ByteBuf` is reference-counted. The project-wide convention:

- **Receiver owns the ref.** A handler receiving a `ByteBuf` (e.g. via
  `channelRead`) is responsible for either passing it on (which transfers the
  ref) or calling `release()`.
- **Sender does not retain unless documented.** A handler that hands a buffer
  to `ctx.fireChannelRead(buf)` or `ctx.write(buf)` no longer owns it.
- **Slices and duplicates start at refCount 1, sharing storage.** A
  `retainedSlice()` increments the parent's refCount; a plain `slice()` does
  not. Choose deliberately.
- **Use `ReferenceCountUtil.release(...)`** when in doubt. Use Netty's
  `ResourceLeakDetector` (default level `SIMPLE` in tests) — leaks fail tests.

## 5. Java 21 idioms

- **Records** for immutable wire-format DTOs (frame headers, capsule
  descriptors, session identifiers). They give free `equals`/`hashCode` and
  serialize cleanly to debug output.
- **Sealed interfaces** for closed-set message hierarchies (`WebTransportFrame`,
  `Capsule`). Pair with pattern matching for exhaustive dispatch:
  ```java
  switch (capsule) {
    case Capsule.MaxStreams m -> handle(m);
    case Capsule.MaxData d    -> handle(d);
    case Capsule.CloseSession c -> handle(c);
    // compiler enforces exhaustiveness
  }
  ```
- **Pattern matching for `instanceof`** where it improves readability.
- **No preview APIs.** `StructuredTaskScope` and `ScopedValue` are still preview
  in 21 and not used. Stick to released APIs.
- **No virtual threads on the event loop.** See §3.

## 6. Datagram fast path

QUIC datagrams (RFC 9221) carry WebTransport datagrams without retransmission.
The path from socket to application:

```
QuicChannel.channelRead(DatagramFrame)
    ↓ (no copy)
WebTransportDatagramHandler
    ↓ readVariableLengthInteger(buf)        ← context-id (quarter stream id)
    ↓ session = sessions.get(contextId)     ← O(1) map lookup
    ↓ buf.readSlice(buf.readableBytes())    ← no copy
    ↓ session.fireDatagramReceived(payload)
```

No allocations beyond the `WebTransportDatagram` wrapper record, and that record
is allocated from a thread-local `Recycler` if profiling shows it matters.

## 7. Allocator tuning knobs

Operators can tune the allocator via JVM properties:

| Property | Purpose |
| --- | --- |
| `-Dio.netty.allocator.type=pooled` | Default; explicit for clarity. |
| `-Dio.netty.allocator.numDirectArenas=N` | Number of direct-buffer arenas. Default = `min(2 * cpus, max-direct-mem / chunkSize / 2 / 3)`. Increase for high-throughput servers; decrease to cap memory. |
| `-Dio.netty.allocator.numHeapArenas=N` | Same for heap-buffer arenas. |
| `WRITE_BUFFER_WATER_MARK` (per channel) | High/low watermark on outbound queue size; controls when `channelWritabilityChanged` fires. |
| `-Dio.netty.leakDetection.level=SIMPLE` | Default in tests; turn up to `PARANOID` to catch leaks under load. |

These are documented for ops, not configured by the library at build time.

## 8. What we don't optimize

The discipline above is for hot paths — frame parsing, datagram routing, stream
data. **Cold paths get clarity, not micro-optimization.** Connection setup,
SETTINGS negotiation, session teardown, error reporting can allocate strings,
use logger formatting, and prefer readable code. PRs that rotate cold-path code
to avoid allocations get pushed back; reviewer time is better spent elsewhere.
