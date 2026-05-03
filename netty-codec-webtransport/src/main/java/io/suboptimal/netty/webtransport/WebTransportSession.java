package io.suboptimal.netty.webtransport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.Future;

/**
 * A WebTransport session multiplexed on an HTTP/3 connection.
 *
 * <p>draft-ietf-webtrans-http3-15 §3.2.
 */
public interface WebTransportSession {

    long sessionId();

    QuicChannel quicChannel();

    QuicStreamChannel connectStream();

    Future<QuicStreamChannel> createBidirectionalStream(ChannelHandler handler);

    Future<QuicStreamChannel> createUnidirectionalStream(ChannelHandler handler);

    void sendDatagram(ByteBuf payload);

    void close(int applicationErrorCode, String applicationErrorMessage);

    void drain();
}
