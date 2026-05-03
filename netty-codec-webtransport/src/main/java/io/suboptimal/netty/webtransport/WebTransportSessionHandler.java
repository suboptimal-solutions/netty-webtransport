package io.suboptimal.netty.webtransport;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.quic.QuicStreamChannel;

/**
 * Application-level handler for a WebTransport session.
 *
 * <p>Implementations override the callbacks they care about. All callbacks run on the QUIC
 * connection's event loop — do not block.
 */
public abstract class WebTransportSessionHandler {

    public void onSessionEstablished(WebTransportSession session) {}

    public void onBidirectionalStream(WebTransportSession session, QuicStreamChannel stream) {}

    public void onUnidirectionalStream(WebTransportSession session, QuicStreamChannel stream) {}

    public void onDatagram(WebTransportSession session, ByteBuf payload) {}

    public void onSessionDraining(WebTransportSession session) {}

    public void onSessionClosed(
            WebTransportSession session, int applicationErrorCode, String applicationErrorMessage) {}
}
