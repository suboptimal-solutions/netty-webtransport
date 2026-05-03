package io.suboptimal.netty.webtransport.internal;

import static io.suboptimal.netty.webtransport.WebTransportProtocol.WT_STREAM_FRAME_TYPE;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.suboptimal.netty.webtransport.VarintCodec;
import io.suboptimal.netty.webtransport.WebTransportSession;
import io.suboptimal.netty.webtransport.WebTransportStreamEvent;
import io.suboptimal.netty.webtransport.WebTransportStreamInitializer;
import java.util.List;

/**
 * First-byte discriminator on a peer-initiated bidirectional QUIC stream. If the leading varint
 * is {@code WT_STREAM} ({@code 0x41}), reads the session ID, hands the stream off to the user's
 * {@link WebTransportStreamInitializer}, and removes the HTTP/3 handlers from the pipeline. Any
 * other leading varint is left for the HTTP/3 layer to parse.
 *
 * <p>draft-ietf-webtrans-http3-15 §4.2.
 */
public final class WebTransportBidiStreamPrefixHandler extends ByteToMessageDecoder {

    private final SessionRegistry registry;
    private final WebTransportStreamInitializer userInit;

    public WebTransportBidiStreamPrefixHandler(
            SessionRegistry registry, WebTransportStreamInitializer userInit) {
        this.registry = registry;
        this.userInit = userInit;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (!VarintCodec.isReadable(in)) return;

        in.markReaderIndex();
        long signalValue = VarintCodec.readVarint(in);

        if (signalValue != WT_STREAM_FRAME_TYPE) {
            in.resetReaderIndex();
            ctx.pipeline().remove(this);
            return;
        }

        if (!VarintCodec.isReadable(in)) {
            in.resetReaderIndex();
            return;
        }
        long sessionId = VarintCodec.readVarint(in);

        DefaultWebTransportSession session = registry.get(sessionId);
        if (session == null) {
            ctx.close();
            return;
        }

        ctx.channel().attr(WebTransportSession.ATTR).set(session);

        removeHttpHandlers(ctx);

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

    private void removeHttpHandlers(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.pipeline();
        List<String> toRemove =
                p.names().stream()
                        .filter(
                                name -> {
                                    ChannelHandler h = p.get(name);
                                    return h != null
                                            && h != this
                                            && h.getClass().getName().startsWith("io.netty.handler.codec.http3.");
                                })
                        .toList();
        toRemove.forEach(p::remove);
    }
}
