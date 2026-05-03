package io.suboptimal.netty.webtransport.example;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.suboptimal.netty.webtransport.WebTransportSession;
import io.suboptimal.netty.webtransport.WebTransportSessionHandler;

public final class EchoSessionHandler extends WebTransportSessionHandler {

    @Override
    public void onSessionEstablished(WebTransportSession session) {
        System.out.println("[echo] Session established: " + session.sessionId());
    }

    @Override
    public void onBidirectionalStream(WebTransportSession session, QuicStreamChannel stream) {
        System.out.println("[echo] Bidi stream opened: " + stream.streamId());
        stream.pipeline().addLast(new EchoStreamHandler());
    }

    @Override
    public void onUnidirectionalStream(WebTransportSession session, QuicStreamChannel stream) {
        System.out.println("[echo] Uni stream opened: " + stream.streamId());
        stream
                .pipeline()
                .addLast(
                        new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                if (msg instanceof ByteBuf buf) {
                                    System.out.println("[echo] Uni stream data: " + buf.readableBytes() + " bytes");
                                    session
                                            .createUnidirectionalStream(new EchoStreamHandler())
                                            .addListener(
                                                    f -> {
                                                        if (f.isSuccess()) {
                                                            QuicStreamChannel reply = (QuicStreamChannel) f.getNow();
                                                            reply.writeAndFlush(buf.retain());
                                                        }
                                                        buf.release();
                                                    });
                                }
                            }
                        });
    }

    @Override
    public void onDatagram(WebTransportSession session, ByteBuf payload) {
        System.out.println("[echo] Datagram: " + payload.readableBytes() + " bytes");
        session.sendDatagram(payload.retain());
    }

    @Override
    public void onSessionClosed(
            WebTransportSession session, int errorCode, String errorMessage) {
        System.out.println("[echo] Session closed: " + errorCode + " " + errorMessage);
    }

    private static final class EchoStreamHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof ByteBuf buf) {
                ctx.writeAndFlush(buf);
            }
        }
    }
}
