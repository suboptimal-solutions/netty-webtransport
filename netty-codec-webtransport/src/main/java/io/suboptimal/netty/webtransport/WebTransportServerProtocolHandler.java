package io.suboptimal.netty.webtransport;

import static io.suboptimal.netty.webtransport.WebTransportProtocol.SETTINGS_WT_ENABLED;
import static io.suboptimal.netty.webtransport.WebTransportProtocol.SETTINGS_WT_INITIAL_MAX_DATA;
import static io.suboptimal.netty.webtransport.WebTransportProtocol.SETTINGS_WT_INITIAL_MAX_STREAMS_BIDI;
import static io.suboptimal.netty.webtransport.WebTransportProtocol.SETTINGS_WT_INITIAL_MAX_STREAMS_UNI;
import static io.suboptimal.netty.webtransport.WebTransportProtocol.WT_UNI_STREAM_TYPE;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http3.DefaultHttp3SettingsFrame;
import io.netty.handler.codec.http3.Http3RequestStreamInitializer;
import io.netty.handler.codec.http3.Http3ServerConnectionHandler;
import io.netty.handler.codec.http3.Http3Settings;
import io.netty.handler.codec.http3.Http3SettingsFrame;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.suboptimal.netty.webtransport.internal.SessionRegistry;
import io.suboptimal.netty.webtransport.internal.WebTransportBidiStreamPrefixHandler;
import io.suboptimal.netty.webtransport.internal.WebTransportConnectHandler;
import io.suboptimal.netty.webtransport.internal.WebTransportDatagramRouter;
import io.suboptimal.netty.webtransport.internal.WebTransportUniStreamPrefixHandler;

/**
 * Top-level handler for WebTransport-capable HTTP/3 server connections. Add to the {@link
 * QuicChannel} pipeline; it installs the HTTP/3 layer with the required WebTransport SETTINGS,
 * the bidi-stream discriminator on every request stream, the unidirectional-stream prefix
 * handler, and the datagram router. Successful CONNECT handshakes promote the request stream into
 * a session channel and run the user-supplied {@link WebTransportSessionInitializer}.
 *
 * <p>draft-ietf-webtrans-http3-15 §3.1.
 */
public final class WebTransportServerProtocolHandler extends ChannelInboundHandlerAdapter {

    private final WebTransportSessionInitializer sessionInit;
    private final WebTransportStreamInitializer bidiInit;
    private final WebTransportUniStreamInitializer uniInit;
    private final long initialMaxStreamsUni;
    private final long initialMaxStreamsBidi;
    private final long initialMaxData;

    private WebTransportServerProtocolHandler(Builder builder) {
        this.sessionInit = builder.sessionInit;
        this.bidiInit = builder.bidiInit;
        this.uniInit = builder.uniInit;
        this.initialMaxStreamsUni = builder.initialMaxStreamsUni;
        this.initialMaxStreamsBidi = builder.initialMaxStreamsBidi;
        this.initialMaxData = builder.initialMaxData;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        SessionRegistry registry = new SessionRegistry();
        Http3SettingsFrame settings = buildSettings();

        Http3RequestStreamInitializer requestInit =
                new Http3RequestStreamInitializer() {
                    @Override
                    protected void initRequestStream(QuicStreamChannel ch) {
                        ch.pipeline()
                                .addFirst(
                                        "wt-bidi-prefix",
                                        new WebTransportBidiStreamPrefixHandler(registry, bidiInit));
                        ch.pipeline()
                                .addLast(
                                        "wt-connect", new WebTransportConnectHandler(registry, sessionInit));
                    }
                };

        ChannelHandler uniStreamFactory =
                new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        ch.pipeline()
                                .addLast(new WebTransportUniStreamPrefixHandler(registry, uniInit));
                    }
                };

        Http3ServerConnectionHandler http3Handler =
                new Http3ServerConnectionHandler(
                        requestInit,
                        null,
                        streamType -> streamType == WT_UNI_STREAM_TYPE ? uniStreamFactory : null,
                        settings,
                        true);

        ctx.pipeline().addAfter(ctx.name(), "http3", http3Handler);
        ctx.pipeline()
                .addAfter("http3", "wt-datagram", new WebTransportDatagramRouter(registry));
    }

    private Http3SettingsFrame buildSettings() {
        Http3Settings settings = Http3Settings.defaultSettings();
        settings.enableConnectProtocol(true);
        settings.enableH3Datagram(true);
        settings.put(SETTINGS_WT_ENABLED, 1L);
        if (initialMaxStreamsUni > 0) {
            settings.put(SETTINGS_WT_INITIAL_MAX_STREAMS_UNI, initialMaxStreamsUni);
        }
        if (initialMaxStreamsBidi > 0) {
            settings.put(SETTINGS_WT_INITIAL_MAX_STREAMS_BIDI, initialMaxStreamsBidi);
        }
        if (initialMaxData > 0) {
            settings.put(SETTINGS_WT_INITIAL_MAX_DATA, initialMaxData);
        }
        return new DefaultHttp3SettingsFrame(settings);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private WebTransportSessionInitializer sessionInit;
        private WebTransportStreamInitializer bidiInit;
        private WebTransportUniStreamInitializer uniInit;
        private long initialMaxStreamsUni = 100;
        private long initialMaxStreamsBidi = 100;
        private long initialMaxData = 1_048_576;

        private Builder() {}

        public Builder session(WebTransportSessionInitializer sessionInit) {
            this.sessionInit = sessionInit;
            return this;
        }

        public Builder bidiStream(WebTransportStreamInitializer bidiInit) {
            this.bidiInit = bidiInit;
            return this;
        }

        public Builder uniStream(WebTransportUniStreamInitializer uniInit) {
            this.uniInit = uniInit;
            return this;
        }

        public Builder initialMaxStreamsUni(long initialMaxStreamsUni) {
            this.initialMaxStreamsUni = initialMaxStreamsUni;
            return this;
        }

        public Builder initialMaxStreamsBidi(long initialMaxStreamsBidi) {
            this.initialMaxStreamsBidi = initialMaxStreamsBidi;
            return this;
        }

        public Builder initialMaxData(long initialMaxData) {
            this.initialMaxData = initialMaxData;
            return this;
        }

        public WebTransportServerProtocolHandler build() {
            return new WebTransportServerProtocolHandler(this);
        }
    }
}
