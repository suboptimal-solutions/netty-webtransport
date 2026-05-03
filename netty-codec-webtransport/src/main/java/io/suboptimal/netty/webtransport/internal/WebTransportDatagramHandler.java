package io.suboptimal.netty.webtransport.internal;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.suboptimal.netty.webtransport.VarintCodec;

/**
 * Handles incoming QUIC datagrams, demuxing by quarter stream ID to the correct session.
 *
 * <p>RFC 9297 §2.1 (HTTP/3 Datagram format), draft-ietf-webtrans-http3-15 §4.5.
 *
 * <p>Pipeline: QuicChannel → [this handler]. Receives raw datagram payloads.
 */
public final class WebTransportDatagramHandler extends ChannelInboundHandlerAdapter {

    private final SessionRegistry sessionRegistry;

    public WebTransportDatagramHandler(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
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

        DefaultWebTransportSession session = sessionRegistry.get(sessionId);
        if (session == null) {
            buf.release();
            return;
        }

        // zero-copy: pass the remaining slice as the payload
        session.sessionHandler().onDatagram(session, buf);
    }
}
