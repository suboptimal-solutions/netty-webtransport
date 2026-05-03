package io.suboptimal.netty.webtransport;

import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.quic.QuicStreamChannel;

/**
 * Initializer for peer-initiated WebTransport <em>bidirectional</em> streams.
 *
 * <p>Runs after the {@code WT_STREAM} (0x41) frame type and the session ID varint have been
 * consumed; user handlers receive payload bytes as plain {@link io.netty.buffer.ByteBuf}
 * {@code channelRead} events.
 *
 * <p>For locally-initiated outbound streams use {@link WebTransportStreamChannelBootstrap#handler}
 * instead — its handler runs after the same prefix has been written.
 *
 * <p>draft-ietf-webtrans-http3-15 §4.2.
 */
public abstract class WebTransportStreamInitializer extends ChannelInitializer<QuicStreamChannel> {

    @Override
    protected final void initChannel(QuicStreamChannel ch) {
        initStream(ch, WebTransportSession.of(ch));
    }

    protected abstract void initStream(
            QuicStreamChannel streamChannel, WebTransportSession session);
}
