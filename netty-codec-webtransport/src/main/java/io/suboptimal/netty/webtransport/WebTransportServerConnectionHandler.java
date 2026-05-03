package io.suboptimal.netty.webtransport;

import static io.suboptimal.netty.webtransport.WebTransportProtocol.*;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http3.DefaultHttp3SettingsFrame;
import io.netty.handler.codec.http3.Http3RequestStreamInitializer;
import io.netty.handler.codec.http3.Http3ServerConnectionHandler;
import io.netty.handler.codec.http3.Http3SettingsFrame;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.suboptimal.netty.webtransport.internal.SessionRegistry;
import io.suboptimal.netty.webtransport.internal.WebTransportDatagramHandler;
import io.suboptimal.netty.webtransport.internal.WebTransportStreamDiscriminator;
import io.suboptimal.netty.webtransport.internal.WebTransportUniStreamHandler;
import java.util.function.Supplier;

/**
 * Top-level handler for WebTransport-capable HTTP/3 server connections.
 *
 * <p>Add this to the {@link QuicChannel} pipeline. It initializes the HTTP/3 layer with the
 * required WebTransport settings and wires up stream/datagram demuxing.
 *
 * <p>draft-ietf-webtrans-http3-15 §3.1.
 */
public final class WebTransportServerConnectionHandler extends ChannelInboundHandlerAdapter {

    private final Supplier<WebTransportSessionHandler> sessionHandlerFactory;
    private final long initialMaxStreamsUni;
    private final long initialMaxStreamsBidi;
    private final long initialMaxData;

    private WebTransportServerConnectionHandler(Builder builder) {
        this.sessionHandlerFactory = builder.sessionHandlerFactory;
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
                                .addFirst("wt-discriminator", new WebTransportStreamDiscriminator(registry));
                        ch.pipeline()
                                .addLast(
                                        "wt-connect-handler",
                                        new WebTransportServerHandler(registry, sessionHandlerFactory));
                    }
                };

        ChannelHandler unknownStreamFactory =
                new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        ch.pipeline().addLast(new WebTransportUniStreamHandler(registry));
                    }
                };

        Http3ServerConnectionHandler http3Handler =
                new Http3ServerConnectionHandler(
                        requestInit,
                        null,
                        streamType -> {
                            if (streamType == WT_UNI_STREAM_TYPE) {
                                return unknownStreamFactory;
                            }
                            return null;
                        },
                        settings,
                        true);

        ctx.pipeline().addAfter(ctx.name(), "http3", http3Handler);
        ctx.pipeline()
                .addAfter("http3", "wt-datagram", new WebTransportDatagramHandler(registry));
    }

    private Http3SettingsFrame buildSettings() {
        DefaultHttp3SettingsFrame settings = new DefaultHttp3SettingsFrame();
        settings.put(SETTINGS_WT_ENABLED, 1L);
        settings.put(SETTINGS_ENABLE_CONNECT_PROTOCOL, 1L);
        settings.put(SETTINGS_H3_DATAGRAM, 1L);
        if (initialMaxStreamsUni > 0) {
            settings.put(SETTINGS_WT_INITIAL_MAX_STREAMS_UNI, initialMaxStreamsUni);
        }
        if (initialMaxStreamsBidi > 0) {
            settings.put(SETTINGS_WT_INITIAL_MAX_STREAMS_BIDI, initialMaxStreamsBidi);
        }
        if (initialMaxData > 0) {
            settings.put(SETTINGS_WT_INITIAL_MAX_DATA, initialMaxData);
        }
        return settings;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Supplier<WebTransportSessionHandler> sessionHandlerFactory;
        private long initialMaxStreamsUni = 100;
        private long initialMaxStreamsBidi = 100;
        private long initialMaxData = 1_048_576;

        private Builder() {}

        public Builder sessionHandlerFactory(
                Supplier<WebTransportSessionHandler> sessionHandlerFactory) {
            this.sessionHandlerFactory = sessionHandlerFactory;
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

        public WebTransportServerConnectionHandler build() {
            if (sessionHandlerFactory == null) {
                throw new IllegalStateException("sessionHandlerFactory is required");
            }
            return new WebTransportServerConnectionHandler(this);
        }
    }
}
