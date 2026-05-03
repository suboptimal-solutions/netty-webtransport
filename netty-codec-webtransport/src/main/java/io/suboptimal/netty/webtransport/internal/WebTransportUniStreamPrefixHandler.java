package io.suboptimal.netty.webtransport.internal;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.suboptimal.netty.webtransport.VarintCodec;
import io.suboptimal.netty.webtransport.WebTransportSession;
import io.suboptimal.netty.webtransport.WebTransportStreamEvent;
import io.suboptimal.netty.webtransport.WebTransportUniStreamInitializer;
import java.util.List;

/**
 * Reads the session-ID varint at the head of a peer-initiated WebTransport unidirectional stream
 * and hands the stream off to the user's {@link WebTransportUniStreamInitializer}. The
 * unidirectional stream type byte ({@code 0x54}) has already been consumed by Netty's HTTP/3
 * layer before this handler receives any data.
 *
 * <p>draft-ietf-webtrans-http3-15 §4.3.
 */
public final class WebTransportUniStreamPrefixHandler extends ByteToMessageDecoder {

    private final SessionRegistry registry;
    private final WebTransportUniStreamInitializer userInit;

    public WebTransportUniStreamPrefixHandler(
            SessionRegistry registry, WebTransportUniStreamInitializer userInit) {
        this.registry = registry;
        this.userInit = userInit;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (!VarintCodec.isReadable(in)) return;

        long sessionId = VarintCodec.readVarint(in);
        DefaultWebTransportSession session = registry.get(sessionId);
        if (session == null) {
            ctx.close();
            return;
        }

        ctx.channel().attr(WebTransportSession.ATTR).set(session);

        ChannelPipeline p = ctx.pipeline();
        if (userInit != null) {
            p.addLast(userInit);
        }

        ctx.fireUserEventTriggered(new WebTransportStreamEvent.Opened(session));

        if (in.isReadable()) {
            out.add(in.readRetainedSlice(in.readableBytes()));
        }

        p.remove(this);
    }
}
