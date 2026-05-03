package io.suboptimal.netty.webtransport.internal;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.suboptimal.netty.webtransport.VarintCodec;
import io.suboptimal.netty.webtransport.WebTransportDatagramFrame;

/**
 * Sits on the parent {@link io.netty.handler.codec.quic.QuicChannel} pipeline and demuxes inbound
 * QUIC datagrams to the right WebTransport session by reading the leading quarter-stream-id
 * varint, then re-fires them as {@link WebTransportDatagramFrame} messages on the session
 * channel's pipeline.
 *
 * <p>RFC 9297 §2.1 (HTTP/3 Datagram format), draft-ietf-webtrans-http3-15 §4.5.
 */
public final class WebTransportDatagramRouter extends ChannelInboundHandlerAdapter {

    private final SessionRegistry registry;

    public WebTransportDatagramRouter(SessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf buf)) {
            ctx.fireChannelRead(msg);
            return;
        }

        if (!VarintCodec.isReadable(buf)) {
            buf.release();
            return;
        }

        long quarterStreamId = VarintCodec.readVarint(buf);
        long sessionId = quarterStreamId * 4;

        DefaultWebTransportSession session = registry.get(sessionId);
        if (session == null) {
            buf.release();
            return;
        }

        WebTransportDatagramFrame frame = new WebTransportDatagramFrame(buf);
        session.sessionChannel().pipeline().fireChannelRead(frame);
    }
}
