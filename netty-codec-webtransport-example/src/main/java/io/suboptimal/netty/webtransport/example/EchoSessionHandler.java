package io.suboptimal.netty.webtransport.example;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.suboptimal.netty.webtransport.WebTransportDatagramFrame;
import io.suboptimal.netty.webtransport.WebTransportSessionEvent;

public final class EchoSessionHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof WebTransportDatagramFrame datagram) {
            System.out.println(
                    "[echo] Datagram: " + datagram.content().readableBytes() + " bytes");
            ctx.writeAndFlush(datagram);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof WebTransportSessionEvent.Established e) {
            System.out.println("[echo] Session established: " + e.session().sessionId());
        } else if (evt instanceof WebTransportSessionEvent.Draining e) {
            System.out.println("[echo] Session draining: " + e.session().sessionId());
        } else if (evt instanceof WebTransportSessionEvent.Closed e) {
            System.out.println(
                    "[echo] Session closed: "
                            + e.applicationErrorCode()
                            + " "
                            + e.applicationErrorMessage());
        }
        ctx.fireUserEventTriggered(evt);
    }
}
