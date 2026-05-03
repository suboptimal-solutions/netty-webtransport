package io.suboptimal.netty.webtransport;

/**
 * User events fired up the session channel pipeline as a WebTransport session moves through its
 * lifecycle.
 *
 * <p>Listen for these in a {@code ChannelInboundHandler} installed via {@link
 * WebTransportSessionInitializer} by overriding {@code userEventTriggered}.
 *
 * <p>draft-ietf-webtrans-http3-15 §3, §6.
 */
public sealed interface WebTransportSessionEvent {

    WebTransportSession session();

    /** The CONNECT handshake completed and the peer accepted the session. */
    record Established(WebTransportSession session) implements WebTransportSessionEvent {}

    /** Peer signalled it intends to stop accepting new streams (DRAIN_WEBTRANSPORT_SESSION). */
    record Draining(WebTransportSession session) implements WebTransportSessionEvent {}

    /**
     * The session has terminated. Fired exactly once: either after a CLOSE_WEBTRANSPORT_SESSION
     * capsule (with the peer-supplied {@code applicationErrorCode} and {@code
     * applicationErrorMessage}) or after the underlying CONNECT stream closed without one (in which
     * case both fields are zero / empty).
     */
    record Closed(
            WebTransportSession session, int applicationErrorCode, String applicationErrorMessage)
            implements WebTransportSessionEvent {}
}
