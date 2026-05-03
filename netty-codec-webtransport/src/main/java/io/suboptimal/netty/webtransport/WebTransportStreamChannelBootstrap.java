package io.suboptimal.netty.webtransport;

import static io.suboptimal.netty.webtransport.WebTransportProtocol.WT_STREAM_FRAME_TYPE;
import static io.suboptimal.netty.webtransport.WebTransportProtocol.WT_UNI_STREAM_TYPE;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.util.concurrent.Future;
import java.util.Objects;

/**
 * Bootstrap for opening outbound WebTransport streams. Mirrors {@code
 * Http2StreamChannelBootstrap}.
 *
 * <p>Obtain via {@link WebTransportSession#streamBootstrap()}. The opened stream is a real Netty
 * {@link QuicStreamChannel} with its own pipeline; the {@code WT_STREAM} frame type (bidi only)
 * and the session ID varint are written by the bootstrap before any user bytes — user handlers
 * see only payload reads/writes. {@link WebTransportSession#ATTR} is set on the stream channel
 * before the user handler is added.
 *
 * <p>draft-ietf-webtrans-http3-15 §4.1.
 */
public final class WebTransportStreamChannelBootstrap {

    private final WebTransportSession session;
    private QuicStreamType type = QuicStreamType.BIDIRECTIONAL;
    private ChannelHandler handler;

    public WebTransportStreamChannelBootstrap(WebTransportSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public WebTransportStreamChannelBootstrap type(QuicStreamType type) {
        this.type = Objects.requireNonNull(type, "type");
        return this;
    }

    public WebTransportStreamChannelBootstrap handler(ChannelHandler handler) {
        this.handler = handler;
        return this;
    }

    public Future<QuicStreamChannel> open() {
        QuicStreamType streamType = this.type;
        long prefixFrameType =
                streamType == QuicStreamType.BIDIRECTIONAL
                        ? WT_STREAM_FRAME_TYPE
                        : WT_UNI_STREAM_TYPE;
        ChannelHandler userHandler = this.handler;
        WebTransportSession localSession = this.session;
        long localSessionId = session.sessionId();

        return session.parentChannel()
                .createStream(
                        streamType,
                        new ChannelInitializer<QuicStreamChannel>() {
                            @Override
                            protected void initChannel(QuicStreamChannel ch) {
                                ch.attr(WebTransportSession.ATTR).set(localSession);

                                ByteBuf prefix = ch.alloc().buffer();
                                VarintCodec.writeVarint(prefix, prefixFrameType);
                                VarintCodec.writeVarint(prefix, localSessionId);
                                ch.write(prefix);

                                if (userHandler != null) {
                                    ch.pipeline().addLast(userHandler);
                                }
                            }
                        });
    }
}
