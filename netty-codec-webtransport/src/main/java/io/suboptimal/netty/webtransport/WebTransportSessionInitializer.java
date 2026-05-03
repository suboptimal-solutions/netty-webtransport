package io.suboptimal.netty.webtransport;

import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.quic.QuicStreamChannel;

/**
 * Initializer for the session channel — the underlying CONNECT request stream — of an accepted
 * WebTransport session.
 *
 * <p>Install user handlers via {@link #initSession(QuicStreamChannel, WebTransportSession)}. Those
 * handlers see {@link WebTransportSessionEvent} user events for lifecycle and {@link
 * WebTransportDatagramFrame} messages for inbound/outbound datagrams. They do <em>not</em> see
 * HTTP/3 frames or capsules — those are consumed internally.
 *
 * <p>draft-ietf-webtrans-http3-15 §3.2.
 */
public abstract class WebTransportSessionInitializer extends ChannelInitializer<QuicStreamChannel> {

    @Override
    protected final void initChannel(QuicStreamChannel ch) {
        initSession(ch, WebTransportSession.of(ch));
    }

    protected abstract void initSession(
            QuicStreamChannel sessionChannel, WebTransportSession session);
}
