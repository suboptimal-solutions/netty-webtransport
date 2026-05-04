package io.suboptimal.netty.webtransport.internal;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
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
        // Fire from `wt-session-out` so the frame is delivered straight to user handlers,
        // skipping the HTTP/3 frame codec at the head of the session pipeline (the codec only
        // understands ByteBuf / QuicStreamFrame and would crash on a WebTransport frame type).
        ChannelPipeline p = session.sessionChannel().pipeline();
        ChannelHandlerContext deliverFrom = p.context("wt-session-out");
        if (deliverFrom == null) {
            // Session not fully initialized yet; drop. Retaining/queueing isn't worth the
            // complexity — the peer will retransmit at a higher layer if needed.
            frame.release();
            return;
        }
        deliverFrom.fireChannelRead(frame);
    }
}
