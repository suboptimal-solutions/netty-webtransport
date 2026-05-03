package io.suboptimal.netty.webtransport.internal;

import static io.suboptimal.netty.webtransport.WebTransportProtocol.METHOD_CONNECT;
import static io.suboptimal.netty.webtransport.WebTransportProtocol.UPGRADE_TOKEN;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.AsciiString;
import io.suboptimal.netty.webtransport.WebTransportSession;
import io.suboptimal.netty.webtransport.WebTransportSessionEvent;
import io.suboptimal.netty.webtransport.WebTransportSessionInitializer;

/**
 * Validates the extended-CONNECT request that opens a WebTransport session, sends the 200
 * response, instantiates the session, and hands control off to {@link WebTransportSessionHandler}
 * for steady-state operation.
 *
 * <p>draft-ietf-webtrans-http3-15 §3.2.
 */
public final class WebTransportConnectHandler extends Http3RequestStreamInboundHandler {

    private static final AsciiString STATUS_OK = AsciiString.cached("200");
    private static final AsciiString STATUS_NOT_FOUND = AsciiString.cached("404");

    private final SessionRegistry registry;
    private final WebTransportSessionInitializer userInit;

    public WebTransportConnectHandler(
            SessionRegistry registry, WebTransportSessionInitializer userInit) {
        this.registry = registry;
        this.userInit = userInit;
    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame headersFrame) {
        CharSequence method = headersFrame.headers().method();
        CharSequence protocol = headersFrame.headers().protocol();

        if (!AsciiString.contentEqualsIgnoreCase(METHOD_CONNECT, method)
                || !AsciiString.contentEqualsIgnoreCase(UPGRADE_TOKEN, protocol)) {
            sendStatus(ctx, STATUS_NOT_FOUND);
            ctx.close();
            return;
        }

        QuicStreamChannel sessionCh = (QuicStreamChannel) ctx.channel();
        QuicChannel parent = (QuicChannel) sessionCh.parent();
        long sessionId = sessionCh.streamId();

        DefaultWebTransportSession session =
                new DefaultWebTransportSession(sessionId, parent, sessionCh);
        registry.register(session);
        sessionCh.attr(WebTransportSession.ATTR).set(session);

        sendStatus(ctx, STATUS_OK);

        ChannelPipeline p = ctx.pipeline();
        p.addAfter(ctx.name(), "wt-session", new WebTransportSessionHandler(registry));
        p.addAfter("wt-session", "wt-session-out", new WebTransportSessionDatagramOutboundHandler());

        if (userInit != null) {
            p.addLast(userInit);
        }

        ctx.fireUserEventTriggered(new WebTransportSessionEvent.Established(session));

        p.remove(this);
    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame dataFrame) {
        // Should not happen — data frames arrive only after a successful handshake, by which point
        // we have replaced ourselves with WebTransportSessionHandler.
        dataFrame.release();
    }

    @Override
    protected void channelInputClosed(ChannelHandlerContext ctx) {
        ctx.close();
    }

    private void sendStatus(ChannelHandlerContext ctx, AsciiString status) {
        DefaultHttp3HeadersFrame response = new DefaultHttp3HeadersFrame();
        response.headers().status(status);
        ctx.writeAndFlush(response);
    }
}
