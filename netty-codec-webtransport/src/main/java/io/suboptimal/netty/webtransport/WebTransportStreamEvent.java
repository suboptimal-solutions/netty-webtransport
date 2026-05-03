package io.suboptimal.netty.webtransport;

/**
 * User events fired up a WebTransport stream channel pipeline.
 *
 * <p>Fired before any payload bytes reach user handlers — listen for {@link Opened} in {@code
 * userEventTriggered} to capture the owning session.
 *
 * <p>draft-ietf-webtrans-http3-15 §4.
 */
public sealed interface WebTransportStreamEvent {

    WebTransportSession session();

    /**
     * Fires once after the WebTransport prefix (frame type for bidi, session ID for both) has been
     * consumed and before the first payload byte is delivered to user handlers.
     */
    record Opened(WebTransportSession session) implements WebTransportStreamEvent {}

    /** Peer reset this stream with the given application error code (draft-15 §4.6). */
    record RemoteReset(WebTransportSession session, long applicationErrorCode)
            implements WebTransportStreamEvent {}
}
