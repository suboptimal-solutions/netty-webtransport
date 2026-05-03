package io.suboptimal.netty.webtransport;

import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.quic.QuicStreamChannel;

/**
 * Initializer for peer-initiated WebTransport <em>unidirectional</em> streams.
 *
 * <p>Runs after the unidirectional stream type ({@code 0x54}) consumed by the HTTP/3 layer and
 * after the session ID varint has been read; user handlers receive payload bytes as plain
 * {@link io.netty.buffer.ByteBuf} {@code channelRead} events.
 *
 * <p>draft-ietf-webtrans-http3-15 §4.3.
 */
public abstract class WebTransportUniStreamInitializer
        extends ChannelInitializer<QuicStreamChannel> {

    @Override
    protected final void initChannel(QuicStreamChannel ch) {
        initStream(ch, WebTransportSession.of(ch));
    }

    protected abstract void initStream(
            QuicStreamChannel streamChannel, WebTransportSession session);
}
