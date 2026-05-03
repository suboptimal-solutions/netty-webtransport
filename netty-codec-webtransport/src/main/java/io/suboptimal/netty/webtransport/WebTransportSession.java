package io.suboptimal.netty.webtransport;

import io.netty.channel.Channel;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.AttributeKey;

/**
 * A WebTransport session multiplexed on an HTTP/3 connection.
 *
 * <p>Reachable from {@link WebTransportSessionEvent#session()} fired on the session channel
 * pipeline, or from any session-owned channel via {@code channel.attr(WebTransportSession.ATTR)}.
 * The session channel and every WebTransport stream channel carry this attribute. Streams and
 * datagrams are <em>not</em> accessed through this interface — they flow through their respective
 * Netty pipelines.
 *
 * <p>draft-ietf-webtrans-http3-15 §3.2.
 */
public interface WebTransportSession {

    /**
     * Channel attribute set on the session channel and on every WebTransport stream channel
     * owned by it.
     */
    AttributeKey<WebTransportSession> ATTR =
            AttributeKey.valueOf(WebTransportSession.class, "session");

    /** Convenience accessor — equivalent to {@code channel.attr(ATTR).get()}. */
    static WebTransportSession of(Channel channel) {
        return channel.attr(ATTR).get();
    }

    /** The peer's session ID, equal to the QUIC stream ID of the underlying CONNECT stream. */
    long sessionId();

    /** The parent QUIC connection that carries this session and its streams and datagrams. */
    QuicChannel parentChannel();

    /**
     * The CONNECT request stream that defines this session's lifetime. User handlers installed via
     * {@link WebTransportSessionInitializer} live on this channel's pipeline; outbound {@link
     * WebTransportDatagramFrame} writes go here.
     */
    QuicStreamChannel sessionChannel();

    /** Bootstrap for opening outbound bidirectional or unidirectional WebTransport streams. */
    WebTransportStreamChannelBootstrap streamBootstrap();

    /**
     * Send a DRAIN_WEBTRANSPORT_SESSION capsule (draft-15 §5.6). The peer is asked to stop opening
     * new streams; existing streams continue.
     */
    void drain();

    /**
     * Send a CLOSE_WEBTRANSPORT_SESSION capsule (draft-15 §5.6) and shut the CONNECT stream's
     * output side. The {@code applicationErrorMessage} must encode to at most 1024 UTF-8 bytes.
     */
    void close(int applicationErrorCode, String applicationErrorMessage);
}
