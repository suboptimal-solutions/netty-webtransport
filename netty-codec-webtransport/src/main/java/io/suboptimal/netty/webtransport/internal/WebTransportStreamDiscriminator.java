package io.suboptimal.netty.webtransport.internal;

import static io.suboptimal.netty.webtransport.WebTransportProtocol.WT_STREAM_FRAME_TYPE;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.suboptimal.netty.webtransport.VarintCodec;
import java.util.List;

/**
 * Sits at the head of a client-initiated bidirectional stream pipeline. Reads the first varint to
 * discriminate between a WebTransport stream (signal value 0x41) and a normal HTTP/3 request
 * stream.
 *
 * <p>draft-ietf-webtrans-http3-15 §4.3.
 */
public final class WebTransportStreamDiscriminator extends ByteToMessageDecoder {

    private final SessionRegistry sessionRegistry;

    public WebTransportStreamDiscriminator(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (!VarintCodec.isReadable(in)) return;

        in.markReaderIndex();
        long signalValue = VarintCodec.readVarint(in);

        if (signalValue != WT_STREAM_FRAME_TYPE) {
            in.resetReaderIndex();
            removeAndPassThrough(ctx);
            return;
        }

        if (!VarintCodec.isReadable(in)) {
            in.resetReaderIndex();
            return;
        }
        long sessionId = VarintCodec.readVarint(in);

        DefaultWebTransportSession session = sessionRegistry.get(sessionId);
        if (session == null) {
            ctx.close();
            return;
        }

        session.incrementOpenedStreamsBidi();
        removeHttpHandlers(ctx);

        io.netty.handler.codec.quic.QuicStreamChannel streamChannel =
                (io.netty.handler.codec.quic.QuicStreamChannel) ctx.channel();
        session.sessionHandler().onBidirectionalStream(session, streamChannel);

        if (in.isReadable()) {
            out.add(in.readRetainedSlice(in.readableBytes()));
        }
        ctx.pipeline().remove(this);
    }

    private void removeAndPassThrough(ChannelHandlerContext ctx) {
        ctx.pipeline().remove(this);
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
