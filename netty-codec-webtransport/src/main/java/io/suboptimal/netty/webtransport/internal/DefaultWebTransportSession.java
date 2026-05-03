package io.suboptimal.netty.webtransport.internal;

import static io.suboptimal.netty.webtransport.WebTransportProtocol.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.util.concurrent.Future;
import io.suboptimal.netty.webtransport.Capsule;
import io.suboptimal.netty.webtransport.CapsuleCodec;
import io.suboptimal.netty.webtransport.VarintCodec;
import io.suboptimal.netty.webtransport.WebTransportSession;
import io.suboptimal.netty.webtransport.WebTransportSessionHandler;

public final class DefaultWebTransportSession implements WebTransportSession {

    private final long sessionId;
    private final QuicChannel quicChannel;
    private final QuicStreamChannel connectStream;
    private final WebTransportSessionHandler handler;
    private final CapsuleCodec capsuleCodec;

    // flow control state
    private long maxStreamsBidi;
    private long maxStreamsUni;
    private long maxData;
    private long openedStreamsBidi;
    private long openedStreamsUni;
    private long sentData;

    public DefaultWebTransportSession(
            long sessionId,
            QuicChannel quicChannel,
            QuicStreamChannel connectStream,
            WebTransportSessionHandler handler) {
        this.sessionId = sessionId;
        this.quicChannel = quicChannel;
        this.connectStream = connectStream;
        this.handler = handler;
        this.capsuleCodec = new CapsuleCodec(connectStream.alloc());
    }

    @Override
    public long sessionId() {
        return sessionId;
    }

    @Override
    public QuicChannel quicChannel() {
        return quicChannel;
    }

    @Override
    public QuicStreamChannel connectStream() {
        return connectStream;
    }

    @Override
    public Future<QuicStreamChannel> createBidirectionalStream(ChannelHandler streamHandler) {
        return quicChannel
                .createStream(
                        QuicStreamType.BIDIRECTIONAL,
                        new ChannelInitializer<QuicStreamChannel>() {
                            @Override
                            protected void initChannel(QuicStreamChannel ch) {
                                ByteBuf header = ch.alloc().buffer();
                                VarintCodec.writeVarint(header, WT_STREAM_FRAME_TYPE);
                                VarintCodec.writeVarint(header, sessionId);
                                ch.write(header);
                                ch.pipeline().addLast(streamHandler);
                            }
                        });
    }

    @Override
    public Future<QuicStreamChannel> createUnidirectionalStream(ChannelHandler streamHandler) {
        return quicChannel
                .createStream(
                        QuicStreamType.UNIDIRECTIONAL,
                        new ChannelInitializer<QuicStreamChannel>() {
                            @Override
                            protected void initChannel(QuicStreamChannel ch) {
                                ByteBuf header = ch.alloc().buffer();
                                VarintCodec.writeVarint(header, WT_UNI_STREAM_TYPE);
                                VarintCodec.writeVarint(header, sessionId);
                                ch.write(header);
                                ch.pipeline().addLast(streamHandler);
                            }
                        });
    }

    @Override
    public void sendDatagram(ByteBuf payload) {
        long quarterStreamId = sessionId / 4;
        ByteBuf datagram = quicChannel.alloc().buffer();
        VarintCodec.writeVarint(datagram, quarterStreamId);
        datagram.writeBytes(payload);
        quicChannel.writeAndFlush(datagram);
        payload.release();
    }

    @Override
    public void close(int applicationErrorCode, String applicationErrorMessage) {
        Capsule.CloseSession capsule =
                new Capsule.CloseSession(applicationErrorCode, applicationErrorMessage);
        ByteBuf buf = connectStream.alloc().buffer();
        Capsule.encode(capsule, buf);
        connectStream.writeAndFlush(buf).addListener(f -> connectStream.shutdownOutput());
    }

    @Override
    public void drain() {
        ByteBuf buf = connectStream.alloc().buffer();
        Capsule.encode(new Capsule.DrainSession(), buf);
        connectStream.writeAndFlush(buf);
    }

    public WebTransportSessionHandler sessionHandler() {
        return handler;
    }

    public CapsuleCodec capsuleCodec() {
        return capsuleCodec;
    }

    // --- Flow control ---

    public void setMaxStreamsBidi(long max) {
        this.maxStreamsBidi = max;
    }

    public void setMaxStreamsUni(long max) {
        this.maxStreamsUni = max;
    }

    public void setMaxData(long max) {
        this.maxData = max;
    }

    public long maxStreamsBidi() {
        return maxStreamsBidi;
    }

    public long maxStreamsUni() {
        return maxStreamsUni;
    }

    public long maxData() {
        return maxData;
    }

    public void incrementOpenedStreamsBidi() {
        openedStreamsBidi++;
    }

    public void incrementOpenedStreamsUni() {
        openedStreamsUni++;
    }

    public long openedStreamsBidi() {
        return openedStreamsBidi;
    }

    public long openedStreamsUni() {
        return openedStreamsUni;
    }

    public void addSentData(long bytes) {
        sentData += bytes;
    }

    public long sentData() {
        return sentData;
    }

    public void destroy() {
        capsuleCodec.release();
    }
}
