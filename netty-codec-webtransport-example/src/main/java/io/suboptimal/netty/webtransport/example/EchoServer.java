package io.suboptimal.netty.webtransport.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
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
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Manual-test WebTransport echo server. Run it, open the printed URL in Chrome, click around.
 *
 * <p>The process binds three things:
 *
 * <ul>
 *   <li>UDP {@code WT_PORT} — QUIC + HTTP/3 + WebTransport (the protocol we're testing).
 *   <li>TCP {@code HTTP_PORT} — a tiny JDK {@link HttpServer} serving the static test page on
 *       {@code /} and the cert hash on {@code /cert.json}. {@code http://localhost:8080} is a
 *       Chrome-recognised secure context, so the page can call {@code new WebTransport(...)}.
 *   <li>Stdout — startup banner with both URLs and the cert hash.
 * </ul>
 *
 * <p>Usage:
 *
 * <pre>
 *   mvn -B -pl netty-codec-webtransport-example -am package
 *   java -cp netty-codec-webtransport-example/target/classes:... \
 *        io.suboptimal.netty.webtransport.example.EchoServer
 * </pre>
 *
 * <p>Default ports: WebTransport on UDP {@code 4433}, test page on TCP {@code 8080}. Override
 * with environment variables {@code WT_PORT} and {@code HTTP_PORT}.
 */
public final class EchoServer {

    private static final int DEFAULT_WT_PORT = 4433;
    private static final int DEFAULT_HTTP_PORT = 8080;

    public static void main(String[] args) throws Exception {
        int wtPort = portFromEnv("WT_PORT", DEFAULT_WT_PORT);
        int httpPort = portFromEnv("HTTP_PORT", DEFAULT_HTTP_PORT);

        // ECDSA P-256, ≤14 days validity — the constraints Chromium enforces for
        // serverCertificateHashes (the JS-side mechanism we use to trust this self-signed cert
        // without a CA chain).
        long now = System.currentTimeMillis();
        SelfSignedCertificate ssc =
                new SelfSignedCertificate(
                        "localhost",
                        new Date(now),
                        new Date(now + TimeUnit.DAYS.toMillis(13)),
                        "EC",
                        256);
        X509Certificate cert = (X509Certificate) ssc.cert();
        byte[] certDer = cert.getEncoded();
        byte[] certSha256 = MessageDigest.getInstance("SHA-256").digest(certDer);
        String certHashBase64 = Base64.getEncoder().encodeToString(certSha256);
        String certHashHex = hex(certSha256);

        WebTransportServerProtocolHandler wtHandler = buildWtHandler();

        QuicSslContext sslContext =
                QuicSslContextBuilder.forServer(ssc.key(), null, cert)
                        .applicationProtocols("h3")
                        .build();

        QuicServerCodecBuilder quicCodec =
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
                                        ch.pipeline().addLast(wtHandler);
                                    }
                                });

        HttpServer httpServer = startHttpFixtureServer(httpPort, certHashBase64, wtPort);

        EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            Channel channel =
                    new Bootstrap()
                            .group(group)
                            .channel(NioDatagramChannel.class)
                            .handler(quicCodec.build())
                            .bind(new InetSocketAddress(wtPort))
                            .sync()
                            .channel();

            printBanner(wtPort, httpPort, certHashBase64, certHashHex);
            channel.closeFuture().sync();
        } finally {
            httpServer.stop(0);
            group.shutdownGracefully();
        }
    }

    private static WebTransportServerProtocolHandler buildWtHandler() {
        return WebTransportServerProtocolHandler.builder()
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
    }

    private static HttpServer startHttpFixtureServer(int httpPort, String certHashBase64, int wtPort)
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", httpPort), 0);
        server.createContext("/cert.json", new CertJsonHandler(certHashBase64, wtPort));
        server.createContext("/", new IndexHtmlHandler());
        server.start();
        return server;
    }

    private static void printBanner(int wtPort, int httpPort, String certHashBase64, String certHashHex) {
        String hr = "==============================================================";
        System.out.println(hr);
        System.out.println(" WebTransport echo server is running.");
        System.out.println();
        System.out.println(" 1. Open this URL in Chrome (or any Chromium-based browser):");
        System.out.println("        http://localhost:" + httpPort + "/");
        System.out.println(" 2. Click \"Connect\" — the page reads /cert.json automatically and");
        System.out.println("    establishes a WebTransport session over UDP " + wtPort + ".");
        System.out.println(" 3. Use the buttons to send bidi streams, uni streams, and datagrams.");
        System.out.println();
        System.out.println(" WT endpoint:    https://localhost:" + wtPort + "/echo");
        System.out.println(" Cert (sha-256): " + certHashBase64 + "  (base64)");
        System.out.println("                 " + certHashHex + "  (hex)");
        System.out.println();
        System.out.println(" Override ports with WT_PORT and HTTP_PORT env vars.");
        System.out.println(" Press Ctrl-C to stop.");
        System.out.println(hr);
    }

    private static int portFromEnv(String key, int fallback) {
        String v = System.getenv(key);
        return v == null ? fallback : Integer.parseInt(v);
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static final class IndexHtmlHandler implements com.sun.net.httpserver.HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws java.io.IOException {
            try (InputStream in =
                    EchoServer.class.getResourceAsStream(
                            "/io/suboptimal/netty/webtransport/example/index.html")) {
                if (in == null) {
                    exchange.sendResponseHeaders(500, 0);
                    exchange.getResponseBody().close();
                    return;
                }
                byte[] body = in.readAllBytes();
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                exchange.getResponseHeaders().add("Cache-Control", "no-store");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.getResponseBody().close();
            }
        }
    }

    private static final class CertJsonHandler implements com.sun.net.httpserver.HttpHandler {
        private final String certHashBase64;
        private final int wtPort;

        CertJsonHandler(String certHashBase64, int wtPort) {
            this.certHashBase64 = certHashBase64;
            this.wtPort = wtPort;
        }

        @Override
        public void handle(HttpExchange exchange) throws java.io.IOException {
            String body =
                    "{\"certHashBase64\":\""
                            + certHashBase64
                            + "\",\"wtPort\":"
                            + wtPort
                            + "}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().add("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        }
    }
}
