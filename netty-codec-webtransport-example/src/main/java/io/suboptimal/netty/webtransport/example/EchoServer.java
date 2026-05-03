package io.suboptimal.netty.webtransport.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.suboptimal.netty.webtransport.WebTransportServerProtocolHandler;
import io.suboptimal.netty.webtransport.WebTransportSession;
import io.suboptimal.netty.webtransport.WebTransportSessionInitializer;
import io.suboptimal.netty.webtransport.WebTransportStreamInitializer;
import io.suboptimal.netty.webtransport.WebTransportUniStreamInitializer;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

/**
 * WebTransport echo server. Accepts sessions and echoes back every bidi/uni stream payload and
 * every datagram.
 *
 * <p>Usage: {@code java -cp ... io.suboptimal.netty.webtransport.example.EchoServer [port]}
 */
public final class EchoServer {

    private static final int DEFAULT_PORT = 4433;

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        SelfSignedCertificate ssc = new SelfSignedCertificate();

        QuicSslContext sslContext =
                QuicSslContextBuilder.forServer(ssc.key(), null, (X509Certificate) ssc.cert())
                        .applicationProtocols("h3")
                        .build();

        WebTransportServerProtocolHandler wtHandler =
                WebTransportServerProtocolHandler.builder()
                        .session(
                                new WebTransportSessionInitializer() {
                                    @Override
                                    protected void initSession(
                                            QuicStreamChannel sessionCh, WebTransportSession session) {
                                        sessionCh.pipeline().addLast(new EchoSessionHandler());
                                    }
                                })
                        .bidiStream(
                                new WebTransportStreamInitializer() {
                                    @Override
                                    protected void initStream(
                                            QuicStreamChannel streamCh, WebTransportSession session) {
                                        streamCh.pipeline().addLast(new EchoStreamHandler());
                                    }
                                })
                        .uniStream(
                                new WebTransportUniStreamInitializer() {
                                    @Override
                                    protected void initStream(
                                            QuicStreamChannel streamCh, WebTransportSession session) {
                                        streamCh.pipeline().addLast(new EchoStreamHandler());
                                    }
                                })
                        .initialMaxStreamsBidi(100)
                        .initialMaxStreamsUni(100)
                        .initialMaxData(1_048_576)
                        .build();

        QuicServerCodecBuilder quicCodec =
                Http3.newQuicServerCodecBuilder()
                        .sslContext(sslContext)
                        .maxIdleTimeout(30_000, TimeUnit.MILLISECONDS)
                        .initialMaxData(10_485_760)
                        .initialMaxStreamDataBidirectionalLocal(1_048_576)
                        .initialMaxStreamDataBidirectionalRemote(1_048_576)
                        .initialMaxStreamDataUnidirectional(1_048_576)
                        .initialMaxStreamsBidirectional(100)
                        .initialMaxStreamsUnidirectional(100)
                        .handler(
                                new ChannelInitializer<QuicChannel>() {
                                    @Override
                                    protected void initChannel(QuicChannel ch) {
                                        ch.pipeline().addLast(wtHandler);
                                    }
                                });

        EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            Channel channel =
                    new Bootstrap()
                            .group(group)
                            .channel(NioDatagramChannel.class)
                            .handler(quicCodec.build())
                            .bind(new InetSocketAddress(port))
                            .sync()
                            .channel();

            System.out.println(
                    "WebTransport echo server listening on https://localhost:" + port + "/echo");
            System.out.println("Certificate SHA-256: " + getCertHash(ssc));
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    private static String getCertHash(SelfSignedCertificate ssc) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ssc.cert().getEncoded());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hash.length; i++) {
                if (i > 0) sb.append(':');
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
