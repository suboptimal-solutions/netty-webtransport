package io.suboptimal.netty.webtransport.internal;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.suboptimal.netty.webtransport.VarintCodec;
import io.suboptimal.netty.webtransport.WebTransportDatagramFrame;
import io.suboptimal.netty.webtransport.WebTransportSession;

/**
 * Intercepts outbound {@link WebTransportDatagramFrame} writes on the session channel, prepends
 * the quarter-stream-id varint, and forwards as a raw datagram on the parent QUIC channel.
 *
 * <p>RFC 9297 §2.1, draft-ietf-webtrans-http3-15 §4.5.
 */
public final class WebTransportSessionDatagramOutboundHandler extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (!(msg instanceof WebTransportDatagramFrame frame)) {
            ctx.write(msg, promise);
            return;
        }

        WebTransportSession session = WebTransportSession.of(ctx.channel());
        if (session == null) {
            frame.release();
            promise.tryFailure(new IllegalStateException("WebTransport session not active"));
            return;
        }

        ByteBuf payload = frame.content();
        long quarterStreamId = session.sessionId() / 4;
        ByteBuf header = ctx.alloc().buffer(VarintCodec.encodedLength(quarterStreamId));
        VarintCodec.writeVarint(header, quarterStreamId);

        ByteBuf datagram = Unpooled.wrappedBuffer(header, payload.retain());
        try {
            session.parentChannel().writeAndFlush(datagram, promise);
        } finally {
            frame.release();
        }
    }
}
