/**
 * WebTransport-over-HTTP/3 codec, handlers, and session API for Netty 4.2.
 *
 * <p>Implements the wire protocol defined in {@code draft-ietf-webtrans-http3-15} on top of
 * {@code io.netty:netty-codec-http3} and {@code io.netty:netty-codec-quic}. Pre-1.0; the API
 * surface is unstable and may change between minor versions. The codec primitives ({@link
 * io.suboptimal.netty.webtransport.VarintCodec VarintCodec}, {@link
 * io.suboptimal.netty.webtransport.Capsule Capsule}, {@link
 * io.suboptimal.netty.webtransport.CapsuleCodec CapsuleCodec}, {@link
 * io.suboptimal.netty.webtransport.WebTransportProtocol WebTransportProtocol}) are stable; the
 * pipeline shell on top of them is what may evolve.
 *
 * <h2>Topology</h2>
 *
 * The shape mirrors Netty's HTTP/2-multiplex idiom (and matches HTTP/3's parent-child channel
 * model). Each WebTransport session is anchored on a single CONNECT request stream — call this
 * the <em>session channel</em> — and each WebTransport stream is its own
 * {@link io.netty.handler.codec.quic.QuicStreamChannel QuicStreamChannel} child with its own
 * pipeline. Datagrams are pipeline messages on the session channel; lifecycle is signalled with
 * Netty user events.
 *
 * <pre>
 * QuicChannel pipeline (per QUIC connection):
 *   [WebTransportServerProtocolHandler]    ← user installs this
 *   [Http3ServerConnectionHandler]         ← installed by us
 *   [WebTransportDatagramRouter]           ← installed by us
 *
 * Session channel (one CONNECT request stream per WebTransport session):
 *   [internal Http3 codec handlers]
 *   [WebTransportSessionHandler — internal]
 *   [WebTransportSessionDatagramOutboundHandler — internal]
 *   [user handlers from WebTransportSessionInitializer]
 *      ├─ channelRead receives WebTransportDatagramFrame
 *      ├─ writeAndFlush a WebTransportDatagramFrame to send a datagram
 *      └─ userEventTriggered receives WebTransportSessionEvent.{Established,Draining,Closed}
 *
 * WebTransport stream channel (one per peer-initiated bidi/uni stream):
 *   [WebTransportBidiStreamPrefixHandler or WebTransportUniStreamPrefixHandler — internal]
 *   [user handlers from WebTransportStreamInitializer / WebTransportUniStreamInitializer]
 *      ├─ channelRead receives plain ByteBuf (the WebTransport prefix is stripped)
 *      ├─ writeAndFlush a ByteBuf to send payload
 *      └─ userEventTriggered receives WebTransportStreamEvent.{Opened,RemoteReset}
 * </pre>
 *
 * <h2>Server-side example</h2>
 *
 * <pre>{@code
 * WebTransportServerProtocolHandler wt =
 *     WebTransportServerProtocolHandler.builder()
 *         .session(new WebTransportSessionInitializer() {
 *             @Override protected void initSession(QuicStreamChannel ch, WebTransportSession s) {
 *                 ch.pipeline().addLast(new MySessionHandler());
 *             }
 *         })
 *         .bidiStream(new WebTransportStreamInitializer() {
 *             @Override protected void initStream(QuicStreamChannel ch, WebTransportSession s) {
 *                 ch.pipeline().addLast(new MyStreamHandler());
 *             }
 *         })
 *         .uniStream(new WebTransportUniStreamInitializer() {
 *             @Override protected void initStream(QuicStreamChannel ch, WebTransportSession s) {
 *                 ch.pipeline().addLast(new MyStreamHandler());
 *             }
 *         })
 *         .initialMaxStreamsBidi(100)
 *         .initialMaxStreamsUni(100)
 *         .initialMaxData(1_048_576)
 *         .build();
 *
 * // Install on every accepted QuicChannel via QuicServerCodecBuilder.handler(...).
 * }</pre>
 *
 * <h2>Opening an outbound stream from the server</h2>
 *
 * <pre>{@code
 * session.streamBootstrap()
 *        .type(QuicStreamType.BIDIRECTIONAL)
 *        .handler(new ChannelInitializer<QuicStreamChannel>() {
 *            @Override protected void initChannel(QuicStreamChannel ch) {
 *                ch.pipeline().addLast(new ServerInitiatedHandler());
 *            }
 *        })
 *        .open()
 *        .addListener((Future<QuicStreamChannel> f) -> {
 *            if (f.isSuccess()) f.getNow().writeAndFlush(payload);
 *        });
 * }</pre>
 *
 * <h2>Refcount and threading</h2>
 *
 * <p>Reference-counting follows Netty's standard rules — see
 * {@code docs/architecture.md} §4. Datagram frames hold their content via
 * {@link io.netty.buffer.DefaultByteBufHolder}; release transfers to whatever pipeline the frame
 * passes through. Stream payloads are plain {@link io.netty.buffer.ByteBuf} reads on the stream
 * channel — same rules as any other Netty handler.
 *
 * <p>All handler code in this package runs on the QUIC connection's event loop. Do not block
 * (no I/O, no synchronous DNS, no virtual threads inside handlers — virtual threads are allowed
 * only in application code that consumes the session API). See {@code docs/architecture.md} §3.
 *
 * <h2>Specs</h2>
 *
 * <p>The vendored specifications, with sha256 sums and fetch dates, live in the {@code specs/}
 * directory at the repository root. {@code docs/wire-format.md} maps each public class to the
 * spec section it implements. {@code docs/architecture.md} describes the design rules every
 * class in this package must follow — zero-copy data path, threading model, refcount discipline.
 */
package io.suboptimal.netty.webtransport;
