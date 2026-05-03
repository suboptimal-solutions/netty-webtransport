package io.suboptimal.netty.webtransport.example;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Echoes back any payload bytes received on a WebTransport stream channel. Bytes arrive as plain
 * {@link ByteBuf} reads — the WebTransport prefix has already been stripped before this handler
 * runs.
 */
public final class EchoStreamHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf buf) {
            System.out.println("[echo] Stream data: " + buf.readableBytes() + " bytes");
            ctx.writeAndFlush(buf);
        } else {
            ctx.fireChannelRead(msg);
        }
    }
}
