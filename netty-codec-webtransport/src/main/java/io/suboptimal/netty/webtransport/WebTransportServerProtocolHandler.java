package io.suboptimal.netty.webtransport;

import static io.suboptimal.netty.webtransport.WebTransportProtocol.SETTINGS_WT_ENABLED;
import static io.suboptimal.netty.webtransport.WebTransportProtocol.SETTINGS_WT_ENABLED_DRAFT02;
import static io.suboptimal.netty.webtransport.WebTransportProtocol.SETTINGS_WT_INITIAL_MAX_DATA;
import static io.suboptimal.netty.webtransport.WebTransportProtocol.SETTINGS_WT_INITIAL_MAX_STREAMS_BIDI;
import static io.suboptimal.netty.webtransport.WebTransportProtocol.SETTINGS_WT_INITIAL_MAX_STREAMS_UNI;
import static io.suboptimal.netty.webtransport.WebTransportProtocol.SETTINGS_WT_MAX_SESSIONS_DRAFT07;
import static io.suboptimal.netty.webtransport.WebTransportProtocol.WT_UNI_STREAM_TYPE;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http3.DefaultHttp3SettingsFrame;
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
 * <p>The handler is {@link Sharable}: build it once and install it on every accepted
 * {@link QuicChannel} via {@code QuicServerCodecBuilder.handler(...)}. All per-channel state is
 * constructed inside {@link #handlerAdded(ChannelHandlerContext)}; the instance fields are
 * immutable configuration.
 *
 * <p>draft-ietf-webtrans-http3-15 §3.1.
 */
@Sharable
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

        // Plain ChannelInitializer, NOT Http3RequestStreamInitializer.
        // Http3ServerConnectionHandler.initBidirectionalStream already installs Http3FrameCodec,
        // encode/decode state validators, and Http3RequestStreamValidationHandler before invoking
        // this initializer. Http3RequestStreamInitializer.initChannel installs the same set, so
        // using it here would put two copies of every HTTP/3 handler in the stream pipeline; the
        // second copy then sees already-decoded frames and explodes with ClassCastException
        // (Http3HeadersFrame -> ByteBuf). All we need is to attach our pre-codec discriminator
        // and post-codec CONNECT handler.
        ChannelInitializer<QuicStreamChannel> requestInit =
                new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
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
                        true,
                        ACCEPT_NON_STANDARD_SETTINGS);

        ctx.pipeline().addAfter(ctx.name(), "http3", http3Handler);
        ctx.pipeline()
                .addAfter("http3", "wt-datagram", new WebTransportDatagramRouter(registry));
    }

    // Netty's default validator silently drops any non-standard SETTINGS — including every
    // WebTransport codepoint we need on the wire. Replace it with one that accepts any
    // non-HTTP/2-reserved id, on both the outbound (our SETTINGS to the peer) and inbound
    // (peer SETTINGS we receive) directions.
    private static final Http3Settings.NonStandardHttp3SettingsValidator
            ACCEPT_NON_STANDARD_SETTINGS = (id, value) -> true;

    private Http3SettingsFrame buildSettings() {
        Http3Settings settings = new Http3Settings(ACCEPT_NON_STANDARD_SETTINGS);
        settings.qpackMaxTableCapacity(0);
        settings.qpackBlockedStreams(0);
        settings.enableConnectProtocol(true);
        settings.enableH3Datagram(true);
        settings.put(SETTINGS_WT_ENABLED, 1L);
        // Advertise draft-02 and draft-07 alongside draft-15 so QUICHE-based peers (Chrome,
        // current production browsers) can negotiate WebTransport. Chrome ships kDraft02
        // by default and gates kDraft07 behind a feature flag, so draft-02 is what actually
        // unblocks browser interop. See §7.1 ("Negotiating the Draft Version") of draft-15.
        settings.put(SETTINGS_WT_ENABLED_DRAFT02, 1L);
        settings.put(
                SETTINGS_WT_MAX_SESSIONS_DRAFT07,
                initialMaxStreamsBidi > 0 ? initialMaxStreamsBidi : 1L);
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
