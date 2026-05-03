package io.suboptimal.netty.webtransport;

import static io.suboptimal.netty.webtransport.WebTransportProtocol.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.AsciiString;
import io.suboptimal.netty.webtransport.internal.DefaultWebTransportSession;
import io.suboptimal.netty.webtransport.internal.SessionRegistry;
import java.util.List;
import java.util.function.Supplier;

/**
 * Handles an HTTP/3 request stream that may be a WebTransport CONNECT request.
 *
 * <p>draft-ietf-webtrans-http3-15 §3.2.
 */
public final class WebTransportServerHandler extends Http3RequestStreamInboundHandler {

    private static final AsciiString STATUS_OK = AsciiString.cached("200");
    private static final AsciiString STATUS_NOT_FOUND = AsciiString.cached("404");

    private final SessionRegistry sessionRegistry;
    private final Supplier<WebTransportSessionHandler> sessionHandlerFactory;
    private DefaultWebTransportSession session;

    public WebTransportServerHandler(
            SessionRegistry sessionRegistry,
            Supplier<WebTransportSessionHandler> sessionHandlerFactory) {
        this.sessionRegistry = sessionRegistry;
        this.sessionHandlerFactory = sessionHandlerFactory;
    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame headersFrame) {
        CharSequence method = headersFrame.headers().method();
        CharSequence protocol = headersFrame.headers().protocol();

        if (!AsciiString.contentEqualsIgnoreCase(METHOD_CONNECT, method)
                || !AsciiString.contentEqualsIgnoreCase(UPGRADE_TOKEN, protocol)) {
            sendResponse(ctx, STATUS_NOT_FOUND);
            ctx.close();
            return;
        }

        QuicStreamChannel connectStream = (QuicStreamChannel) ctx.channel();
        QuicChannel quicChannel = (QuicChannel) connectStream.parent();
        long sessionId = connectStream.streamId();

        WebTransportSessionHandler handler = sessionHandlerFactory.get();
        session =
                new DefaultWebTransportSession(sessionId, quicChannel, connectStream, handler);
        sessionRegistry.register(session);

        sendResponse(ctx, STATUS_OK);

        handler.onSessionEstablished(session);
    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame dataFrame) {
        if (session == null) {
            dataFrame.release();
            return;
        }

        ByteBuf content = dataFrame.content();
        List<Capsule> capsules = session.capsuleCodec().decode(content);
        dataFrame.release();

        for (Capsule capsule : capsules) {
            handleCapsule(ctx, capsule);
        }
    }

    private void handleCapsule(ChannelHandlerContext ctx, Capsule capsule) {
        switch (capsule) {
            case Capsule.CloseSession c -> {
                sessionRegistry.remove(session.sessionId());
                session
                        .sessionHandler()
                        .onSessionClosed(session, c.applicationErrorCode(), c.applicationErrorMessage());
                session.destroy();
                ctx.close();
            }
            case Capsule.DrainSession ignored -> session.sessionHandler().onSessionDraining(session);
            case Capsule.MaxStreamsBidi m -> session.setMaxStreamsBidi(m.maximumStreams());
            case Capsule.MaxStreamsUni m -> session.setMaxStreamsUni(m.maximumStreams());
            case Capsule.MaxData m -> session.setMaxData(m.maximumData());
            case Capsule.StreamsBlockedBidi ignored -> {}
            case Capsule.StreamsBlockedUni ignored -> {}
            case Capsule.DataBlocked ignored -> {}
            case Capsule.Unknown ignored -> {}
        }
    }

    @Override
    protected void channelInputClosed(ChannelHandlerContext ctx) {
        if (session != null) {
            sessionRegistry.remove(session.sessionId());
            session.sessionHandler().onSessionClosed(session, 0, "");
            session.destroy();
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, AsciiString status) {
        DefaultHttp3HeadersFrame response = new DefaultHttp3HeadersFrame();
        response.headers().status(status);
        ctx.writeAndFlush(response);
    }
}
