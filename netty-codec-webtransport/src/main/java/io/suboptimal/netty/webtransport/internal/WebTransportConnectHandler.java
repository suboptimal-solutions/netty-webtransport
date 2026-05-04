package io.suboptimal.netty.webtransport.internal;

import static io.suboptimal.netty.webtransport.WebTransportProtocol.METHOD_CONNECT;
import static io.suboptimal.netty.webtransport.WebTransportProtocol.UPGRADE_TOKEN;
import static io.suboptimal.netty.webtransport.WebTransportProtocol.UPGRADE_TOKEN_DRAFT07;

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

    // draft-02 §3.3: client offers each draft it supports via Sec-Webtransport-Http3-Draft<NN>: 1
    // headers; the server confirms the chosen draft with a single Sec-Webtransport-Http3-Draft
    // response header. Chrome refuses to complete the handshake without this echo, so we mirror
    // back any offered draft (defaulting to draft02 since that's the only one current Chromium
    // ships by default).
    private static final AsciiString HEADER_DRAFT02_OFFER =
            AsciiString.cached("sec-webtransport-http3-draft02");
    private static final AsciiString HEADER_DRAFT_SELECTED =
            AsciiString.cached("sec-webtransport-http3-draft");
    private static final AsciiString DRAFT_VALUE_DRAFT02 = AsciiString.cached("draft02");

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

        boolean validToken =
                AsciiString.contentEqualsIgnoreCase(UPGRADE_TOKEN, protocol)
                        || AsciiString.contentEqualsIgnoreCase(UPGRADE_TOKEN_DRAFT07, protocol);
        if (!AsciiString.contentEqualsIgnoreCase(METHOD_CONNECT, method) || !validToken) {
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

        AsciiString selectedDraft = null;
        if (headersFrame.headers().contains(HEADER_DRAFT02_OFFER)) {
            selectedDraft = DRAFT_VALUE_DRAFT02;
        }
        sendOk(ctx, selectedDraft);

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

    private void sendOk(ChannelHandlerContext ctx, AsciiString selectedDraft) {
        DefaultHttp3HeadersFrame response = new DefaultHttp3HeadersFrame();
        response.headers().status(STATUS_OK);
        if (selectedDraft != null) {
            response.headers().add(HEADER_DRAFT_SELECTED, selectedDraft);
        }
        ctx.writeAndFlush(response);
    }
}
