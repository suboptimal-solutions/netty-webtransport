# netty-codec-webtransport

WebTransport-over-HTTP/3 server (and, eventually, Java client) built on
[Netty](https://netty.io). The shape mirrors Netty's HTTP/2-multiplex idiom:
each WebTransport stream is its own `QuicStreamChannel` with its own pipeline,
datagrams arrive as `WebTransportDatagramFrame` channel reads, and session
lifecycle fires as Netty user events.

## Status

Pre-1.0. The codec primitives (varint, capsule), server pipeline, session API,
per-stream child channels, and datagram routing are implemented; flow-control
enforcement and a Java client are still pending. See
[docs/roadmap.md](docs/roadmap.md) for the phased plan; the priority is a
server interoperable with Chrome's `new WebTransport(...)` JS API.

## Targeting

| Spec | Version |
| --- | --- |
| WebTransport overview | [`draft-ietf-webtrans-overview-12`](specs/draft-ietf-webtrans-overview-12.txt) |
| WebTransport over HTTP/3 (wire protocol) | [`draft-ietf-webtrans-http3-15`](specs/draft-ietf-webtrans-http3-15.txt) |
| WebTransport over HTTP/2 (fallback, future) | [`draft-ietf-webtrans-http2-14`](specs/draft-ietf-webtrans-http2-14.txt) |
| Extended CONNECT | [`RFC 9220`](specs/rfc9220.txt) |
| QUIC datagram | [`RFC 9221`](specs/rfc9221.txt) |
| HTTP datagrams + Capsule Protocol | [`RFC 9297`](specs/rfc9297.txt) |
| W3C WebTransport (browser API) | [`w3c-webtransport.html`](specs/w3c-webtransport.html) |

The exact bytes vendored at bootstrap, with sha256 sums and fetch dates, are
recorded in [specs/README.md](specs/README.md).

## Why another WebTransport implementation?

- **Native to Netty 4.2** — uses the graduated `io.netty:netty-codec-http3` and
  `io.netty:netty-codec-quic` artifacts (in `io.netty` core since 4.2.1.Final,
  May 2025). No incubator coordinates.
- **Zero-copy on data-intensive paths** — direct `ByteBuf`, `slice()` /
  `retainedSlice()` for splits, `CompositeByteBuf` for assembly. No `byte[]` or
  `String` materialization on hot paths. See [docs/architecture.md](docs/architecture.md).
- **Java 21 idioms** — records for immutable frame DTOs, sealed interfaces for
  closed message hierarchies, pattern matching for frame dispatch.

## Requirements

- JDK 21 (LTS)
- Apache Maven 3.9+ (use your locally installed `mvn`; no wrapper is shipped)

## Build

```sh
mvn -B verify          # full build, runs tests and Spotless format check
mvn spotless:apply     # auto-fix Java formatting (Google Java Format)
```

`spotless:check` runs as part of `verify`, so unformatted code fails the build
locally and in CI. Run `spotless:apply` before committing.

## API at a glance

```java
WebTransportServerProtocolHandler wt =
    WebTransportServerProtocolHandler.builder()
        .session(new WebTransportSessionInitializer() {
            @Override protected void initSession(QuicStreamChannel ch, WebTransportSession s) {
                ch.pipeline().addLast(new MySessionHandler());      // sees datagrams + lifecycle
            }
        })
        .bidiStream(new WebTransportStreamInitializer() {
            @Override protected void initStream(QuicStreamChannel ch, WebTransportSession s) {
                ch.pipeline().addLast(new MyStreamHandler());       // sees raw payload ByteBufs
            }
        })
        .uniStream(new WebTransportUniStreamInitializer() {
            @Override protected void initStream(QuicStreamChannel ch, WebTransportSession s) {
                ch.pipeline().addLast(new MyStreamHandler());
            }
        })
        .build();

// Add wt to a QuicChannel pipeline (typically inside a ChannelInitializer<QuicChannel>
// passed to QuicServerCodecBuilder.handler(...)).
```

User handlers see:

- **`WebTransportSessionEvent.Established` / `Draining` / `Closed`** as `userEventTriggered`
  events on the session-channel pipeline (mirrors `WebSocketServerProtocolHandler.HandshakeComplete`).
- **`WebTransportDatagramFrame`** as `channelRead` events on the session-channel
  pipeline; outbound datagrams are `ctx.writeAndFlush(new WebTransportDatagramFrame(buf))`
  on the same channel.
- **Plain `ByteBuf`** `channelRead` events on each WebTransport stream channel —
  the WebTransport prefix (frame type for bidi, session ID) is stripped before user
  handlers see the data.

To open a stream from the server side: `session.streamBootstrap().type(BIDIRECTIONAL).handler(...).open()`
(mirrors `Http2StreamChannelBootstrap`).

A complete runnable example is in
[`netty-codec-webtransport-example`](netty-codec-webtransport-example/).

## Module map

| Directory | Artifact | Role |
| --- | --- | --- |
| [`netty-codec-webtransport/`](netty-codec-webtransport/) | `netty-codec-webtransport` | The codec — frames, capsules, server handlers, session API. Mirrors `netty-codec-http3`. |
| [`netty-codec-webtransport-example/`](netty-codec-webtransport-example/) | `netty-codec-webtransport-example` | Runnable demos: `EchoServer` accepts WebTransport sessions and echoes streams + datagrams. |

## Documentation

- [docs/architecture.md](docs/architecture.md) — design rules: zero-copy data
  path, threading model, reference-counting discipline, Java 21 idioms.
- [docs/netty-stack.md](docs/netty-stack.md) — Netty 4.2 dependency map, native
  classifiers, JDK requirements, gaps the codec must fill.
- [docs/specs.md](docs/specs.md) — curated reading guide into the vendored specs.
- [docs/project-layout.md](docs/project-layout.md) — module / package conventions
  and Maven layout rationale.
- [docs/roadmap.md](docs/roadmap.md) — phased implementation plan.
- [docs/wire-format.md](docs/wire-format.md) — spec-to-class map: which codec
  class implements which section of which spec.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE.txt](NOTICE.txt).
The license is asserted at the repository root only; individual `.java` files
do not carry per-file license headers.

## Security

Security reports go to the address in [SECURITY.md](SECURITY.md), not to public
issue trackers.
