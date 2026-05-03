package io.suboptimal.netty.webtransport.internal;

import static io.suboptimal.netty.webtransport.WebTransportProtocol.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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

    private long maxStreamsBidi;
    private long maxStreamsUni;
    private long maxData;

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
        return openStream(QuicStreamType.BIDIRECTIONAL, WT_STREAM_FRAME_TYPE, streamHandler);
    }

    @Override
    public Future<QuicStreamChannel> createUnidirectionalStream(ChannelHandler streamHandler) {
        return openStream(QuicStreamType.UNIDIRECTIONAL, WT_UNI_STREAM_TYPE, streamHandler);
    }

    private Future<QuicStreamChannel> openStream(
            QuicStreamType type, long headerType, ChannelHandler streamHandler) {
        return quicChannel.createStream(
                type,
                new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        ByteBuf header = ch.alloc().buffer();
                        VarintCodec.writeVarint(header, headerType);
                        VarintCodec.writeVarint(header, sessionId);
                        ch.write(header);
                        ch.pipeline().addLast(streamHandler);
                    }
                });
    }

    @Override
    public void sendDatagram(ByteBuf payload) {
        long quarterStreamId = sessionId / 4;
        ByteBuf header = quicChannel.alloc().buffer(VarintCodec.encodedLength(quarterStreamId));
        VarintCodec.writeVarint(header, quarterStreamId);
        quicChannel.writeAndFlush(Unpooled.wrappedBuffer(header, payload));
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

    public void destroy() {
        capsuleCodec.release();
    }
}
