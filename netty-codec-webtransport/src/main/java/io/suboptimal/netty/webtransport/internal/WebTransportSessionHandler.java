package io.suboptimal.netty.webtransport.internal;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler;
import io.suboptimal.netty.webtransport.Capsule;
import io.suboptimal.netty.webtransport.WebTransportSession;
import io.suboptimal.netty.webtransport.WebTransportSessionEvent;
import java.util.List;

/**
 * Steady-state inbound handler installed on the session channel after CONNECT succeeds. Decodes
 * capsules carried in HTTP/3 DATA frames, applies flow-control state to the session, and fires
 * {@link WebTransportSessionEvent} user events up the pipeline.
 *
 * <p>draft-ietf-webtrans-http3-15 §5.6, RFC 9297 §3.2.
 */
public final class WebTransportSessionHandler extends Http3RequestStreamInboundHandler {

    private final SessionRegistry registry;

    public WebTransportSessionHandler(SessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame headersFrame) {
        // Trailers or unexpected headers; ignore.
    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame dataFrame) {
        DefaultWebTransportSession session = sessionOf(ctx);
        if (session == null) {
            dataFrame.release();
            return;
        }
        ByteBuf content = dataFrame.content();
        List<Capsule> capsules = session.capsuleCodec().decode(content);
        dataFrame.release();
        for (Capsule capsule : capsules) {
            handleCapsule(ctx, session, capsule);
        }
    }

    private void handleCapsule(
            ChannelHandlerContext ctx, DefaultWebTransportSession session, Capsule capsule) {
        switch (capsule) {
            case Capsule.CloseSession c -> {
                if (registry.remove(session.sessionId()) != null) {
                    ctx.fireUserEventTriggered(
                            new WebTransportSessionEvent.Closed(
                                    session, c.applicationErrorCode(), c.applicationErrorMessage()));
                    session.destroy();
                }
                ctx.close();
            }
            case Capsule.DrainSession ignored ->
                    ctx.fireUserEventTriggered(new WebTransportSessionEvent.Draining(session));
            case Capsule.MaxStreamsBidi m -> session.setMaxStreamsBidi(m.maximumStreams());
            case Capsule.MaxStreamsUni m -> session.setMaxStreamsUni(m.maximumStreams());
            case Capsule.MaxData m -> session.setMaxData(m.maximumData());
            case Capsule.StreamsBlockedBidi ignored -> {}
            case Capsule.StreamsBlockedUni ignored -> {}
            case Capsule.DataBlocked ignored -> {}
            case Capsule.Unknown u -> u.payload().release();
        }
    }

    @Override
    protected void channelInputClosed(ChannelHandlerContext ctx) {
        DefaultWebTransportSession session = sessionOf(ctx);
        if (session != null && registry.remove(session.sessionId()) != null) {
            ctx.fireUserEventTriggered(new WebTransportSessionEvent.Closed(session, 0, ""));
            session.destroy();
        }
    }

    private static DefaultWebTransportSession sessionOf(ChannelHandlerContext ctx) {
        WebTransportSession s = WebTransportSession.of(ctx.channel());
        return s instanceof DefaultWebTransportSession d ? d : null;
    }
}
