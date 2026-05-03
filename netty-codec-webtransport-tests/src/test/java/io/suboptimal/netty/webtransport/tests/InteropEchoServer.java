package io.suboptimal.netty.webtransport.tests;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.concurrent.Future;
import io.suboptimal.netty.webtransport.WebTransportDatagramFrame;
import io.suboptimal.netty.webtransport.WebTransportServerProtocolHandler;
import io.suboptimal.netty.webtransport.WebTransportSession;
import io.suboptimal.netty.webtransport.WebTransportSessionEvent;
import io.suboptimal.netty.webtransport.WebTransportSessionInitializer;
import io.suboptimal.netty.webtransport.WebTransportStreamInitializer;
import io.suboptimal.netty.webtransport.WebTransportUniStreamInitializer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * In-process WebTransport server fixture for browser interop tests.
 *
 * <p>Echoes bidi/uni stream payloads and datagrams back to the client; records every observable
 * event into {@link #observations()} so tests can assert on the server-side view.
 *
 * <p>The TLS certificate is generated per-instance with {@link SelfSignedCertificate}; the
 * {@link #spkiHash()} is what gets passed to Chrome via
 * {@code --ignore-certificate-errors-spki-list}. The bound {@link #port()} is auto-assigned —
 * each test gets its own port to avoid collisions.
 */
final class InteropEchoServer implements AutoCloseable {

    private final BlockingQueue<Observation> observations = new LinkedBlockingQueue<>();
    private final Map<Long, WebTransportSession> sessions = new ConcurrentHashMap<>();

    private EventLoopGroup group;
    private Channel channel;
    private SelfSignedCertificate ssc;
    private int port;
    private String spkiHash;
    private byte[] certSha256;

    static InteropEchoServer start() throws Exception {
        InteropEchoServer s = new InteropEchoServer();
        s.run();
        return s;
    }

    private void run() throws Exception {
        // Chrome's WebTransport API trusts a self-signed cert without a CA chain when the JS
        // call passes `serverCertificateHashes`, but the cert itself must satisfy three rules:
        //   1. Algorithm: ECDSA with curve P-256 (a.k.a. EC / 256 bits in JCA terminology).
        //   2. Validity: ≤14 days from "now".
        //   3. Subject must match the connected hostname (localhost).
        // We meet all three here.
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now);
        Date notAfter = new Date(now + TimeUnit.DAYS.toMillis(13));
        ssc = new SelfSignedCertificate("localhost", notBefore, notAfter, "EC", 256);

        X509Certificate cert = (X509Certificate) ssc.cert();
        spkiHash = SpkiHash.of(cert);
        certSha256 = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());

        QuicSslContext sslContext =
                QuicSslContextBuilder.forServer(ssc.key(), null, (X509Certificate) ssc.cert())
                        .applicationProtocols("h3")
                        .build();

        WebTransportServerProtocolHandler wt =
                WebTransportServerProtocolHandler.builder()
                        .session(
                                new WebTransportSessionInitializer() {
                                    @Override
                                    protected void initSession(
                                            QuicStreamChannel ch, WebTransportSession session) {
                                        ch.pipeline()
                                                .addLast(new SessionObserver(observations, sessions));
                                    }
                                })
                        .bidiStream(
                                new WebTransportStreamInitializer() {
                                    @Override
                                    protected void initStream(
                                            QuicStreamChannel ch, WebTransportSession session) {
                                        ch.pipeline().addLast(new BidiEcho(observations, session));
                                    }
                                })
                        .uniStream(
                                new WebTransportUniStreamInitializer() {
                                    @Override
                                    protected void initStream(
                                            QuicStreamChannel ch, WebTransportSession session) {
                                        ch.pipeline().addLast(new UniEcho(observations, session));
                                    }
                                })
                        .initialMaxStreamsBidi(100)
                        .initialMaxStreamsUni(100)
                        .initialMaxData(1_048_576)
                        .build();

        ChannelHandler quicCodec =
                Http3.newQuicServerCodecBuilder()
                        .sslContext(sslContext)
                        .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                        .maxIdleTimeout(30_000, TimeUnit.MILLISECONDS)
                        .initialMaxData(10_485_760)
                        .initialMaxStreamDataBidirectionalLocal(1_048_576)
                        .initialMaxStreamDataBidirectionalRemote(1_048_576)
                        .initialMaxStreamDataUnidirectional(1_048_576)
                        .initialMaxStreamsBidirectional(100)
                        .initialMaxStreamsUnidirectional(100)
                        .datagram(2048, 2048)
                        .handler(
                                new ChannelInitializer<QuicChannel>() {
                                    @Override
                                    protected void initChannel(QuicChannel ch) {
                                        ch.pipeline().addLast(wt);
                                    }
                                })
                        .build();

        group = new NioEventLoopGroup(1);
        channel =
                new Bootstrap()
                        .group(group)
                        .channel(NioDatagramChannel.class)
                        .handler(quicCodec)
                        .bind(new InetSocketAddress("127.0.0.1", 0))
                        .sync()
                        .channel();
        port = ((InetSocketAddress) channel.localAddress()).getPort();
    }

    int port() {
        return port;
    }

    String spkiHash() {
        return spkiHash;
    }

    /** Raw SHA-256 of the DER-encoded certificate, for {@code serverCertificateHashes}. */
    byte[] certSha256() {
        return certSha256;
    }

    BlockingQueue<Observation> observations() {
        return observations;
    }

    /** Open a server-initiated bidirectional stream and write a greeting payload. */
    Future<QuicStreamChannel> openBidiStream(long sessionId, String greeting) {
        WebTransportSession session = sessions.get(sessionId);
        if (session == null) throw new IllegalStateException("no such session: " + sessionId);
        Future<QuicStreamChannel> future =
                session.streamBootstrap().type(QuicStreamType.BIDIRECTIONAL).open();
        future.addListener(
                f -> {
                    if (f.isSuccess()) {
                        QuicStreamChannel ch = (QuicStreamChannel) f.getNow();
                        ByteBuf payload =
                                Unpooled.copiedBuffer(greeting, StandardCharsets.UTF_8);
                        ch.writeAndFlush(payload).addListener(g -> ch.shutdownOutput());
                    }
                });
        return future;
    }

    /** Send DRAIN_WEBTRANSPORT_SESSION on the named session. */
    void drainSession(long sessionId) {
        WebTransportSession session = sessions.get(sessionId);
        if (session == null) throw new IllegalStateException("no such session: " + sessionId);
        session.drain();
    }

    @Override
    public void close() {
        if (channel != null) {
            try {
                channel.close().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (group != null) {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    /** Records lifecycle events; echoes inbound datagrams back to the same session channel. */
    private static final class SessionObserver extends ChannelInboundHandlerAdapter {
        private final BlockingQueue<Observation> observations;
        private final Map<Long, WebTransportSession> sessions;

        SessionObserver(
                BlockingQueue<Observation> observations,
                Map<Long, WebTransportSession> sessions) {
            this.observations = observations;
            this.sessions = sessions;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof WebTransportDatagramFrame frame) {
                WebTransportSession session = WebTransportSession.of(ctx.channel());
                String text = frame.content().toString(StandardCharsets.UTF_8);
                observations.add(new Observation.DatagramReceived(session.sessionId(), text));
                // Echo: write a fresh frame holding a copy of the payload bytes.
                ByteBuf echo =
                        Unpooled.copiedBuffer(text.getBytes(StandardCharsets.UTF_8));
                ctx.writeAndFlush(new WebTransportDatagramFrame(echo));
                frame.release();
                return;
            }
            ctx.fireChannelRead(msg);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof WebTransportSessionEvent.Established e) {
                sessions.put(e.session().sessionId(), e.session());
                observations.add(new Observation.SessionEstablished(e.session().sessionId()));
            } else if (evt instanceof WebTransportSessionEvent.Draining e) {
                observations.add(new Observation.SessionDraining(e.session().sessionId()));
            } else if (evt instanceof WebTransportSessionEvent.Closed e) {
                sessions.remove(e.session().sessionId());
                observations.add(
                        new Observation.SessionClosed(
                                e.session().sessionId(),
                                e.applicationErrorCode(),
                                e.applicationErrorMessage()));
            }
            ctx.fireUserEventTriggered(evt);
        }
    }

    /** Echoes the entire bidi stream back on the same channel. */
    private static final class BidiEcho extends ChannelInboundHandlerAdapter {
        private final BlockingQueue<Observation> observations;
        private final WebTransportSession session;
        private boolean firstChunk = true;

        BidiEcho(BlockingQueue<Observation> observations, WebTransportSession session) {
            this.observations = observations;
            this.session = session;
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof ByteBuf buf) {
                if (firstChunk) {
                    observations.add(new Observation.BidiStreamOpened(session.sessionId()));
                    firstChunk = false;
                }
                String text = buf.toString(StandardCharsets.UTF_8);
                observations.add(new Observation.StreamPayload(session.sessionId(), text));
                ctx.writeAndFlush(buf.retainedDuplicate());
                buf.release();
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            ((QuicStreamChannel) ctx.channel()).shutdownOutput();
            ctx.fireChannelInactive();
        }
    }

    /**
     * Reads all bytes off a peer-initiated unidirectional stream and replies on a
     * server-initiated unidirectional stream with the same payload.
     */
    private static final class UniEcho extends ChannelInboundHandlerAdapter {
        private final BlockingQueue<Observation> observations;
        private final WebTransportSession session;
        private final ByteBuf accumulator = Unpooled.buffer();
        private boolean opened;

        UniEcho(BlockingQueue<Observation> observations, WebTransportSession session) {
            this.observations = observations;
            this.session = session;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof ByteBuf buf) {
                if (!opened) {
                    observations.add(new Observation.UniStreamOpened(session.sessionId()));
                    opened = true;
                }
                accumulator.writeBytes(buf);
                buf.release();
                return;
            }
            ctx.fireChannelRead(msg);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            String text = accumulator.toString(StandardCharsets.UTF_8);
            observations.add(new Observation.StreamPayload(session.sessionId(), text));

            ByteBuf reply =
                    Unpooled.copiedBuffer(text, StandardCharsets.UTF_8);
            session.streamBootstrap()
                    .type(QuicStreamType.UNIDIRECTIONAL)
                    .open()
                    .addListener(
                            (Future<QuicStreamChannel> f) -> {
                                if (f.isSuccess()) {
                                    QuicStreamChannel ch = f.getNow();
                                    ChannelFuture write = ch.writeAndFlush(reply);
                                    write.addListener(g -> ch.shutdownOutput());
                                } else {
                                    reply.release();
                                }
                            });
            accumulator.release();
            ctx.fireChannelInactive();
        }
    }
}
