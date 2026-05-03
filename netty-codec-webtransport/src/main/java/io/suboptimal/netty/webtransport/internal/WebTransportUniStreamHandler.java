package io.suboptimal.netty.webtransport.internal;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.suboptimal.netty.webtransport.VarintCodec;
import java.util.List;

/**
 * Handles a WebTransport unidirectional stream (type 0x54). Reads the session ID varint and
 * dispatches to the session.
 *
 * <p>draft-ietf-webtrans-http3-15 §4.2. The stream type varint has already been consumed by
 * Netty's HTTP/3 unidirectional stream handler before this handler receives data.
 */
public final class WebTransportUniStreamHandler extends ByteToMessageDecoder {

    private final SessionRegistry sessionRegistry;

    public WebTransportUniStreamHandler(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (!VarintCodec.isReadable(in)) return;

        long sessionId = VarintCodec.readVarint(in);

        DefaultWebTransportSession session = sessionRegistry.get(sessionId);
        if (session == null) {
            ctx.close();
            return;
        }

        QuicStreamChannel streamChannel = (QuicStreamChannel) ctx.channel();
        session.sessionHandler().onUnidirectionalStream(session, streamChannel);

        if (in.isReadable()) {
            out.add(in.readRetainedSlice(in.readableBytes()));
        }
        ctx.pipeline().remove(this);
    }
}
