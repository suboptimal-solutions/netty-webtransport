package io.suboptimal.netty.webtransport.internal;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http3.DefaultHttp3DataFrame;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.suboptimal.netty.webtransport.Capsule;
import io.suboptimal.netty.webtransport.CapsuleCodec;
import io.suboptimal.netty.webtransport.WebTransportSession;
import io.suboptimal.netty.webtransport.WebTransportStreamChannelBootstrap;

public final class DefaultWebTransportSession implements WebTransportSession {

    private final long sessionId;
    private final QuicChannel parentChannel;
    private final QuicStreamChannel sessionChannel;
    private final CapsuleCodec capsuleCodec;

    private long maxStreamsBidi;
    private long maxStreamsUni;
    private long maxData;

    public DefaultWebTransportSession(
            long sessionId, QuicChannel parentChannel, QuicStreamChannel sessionChannel) {
        this.sessionId = sessionId;
        this.parentChannel = parentChannel;
        this.sessionChannel = sessionChannel;
        this.capsuleCodec = new CapsuleCodec(sessionChannel.alloc());
    }

    @Override
    public long sessionId() {
        return sessionId;
    }

    @Override
    public QuicChannel parentChannel() {
        return parentChannel;
    }

    @Override
    public QuicStreamChannel sessionChannel() {
        return sessionChannel;
    }

    @Override
    public WebTransportStreamChannelBootstrap streamBootstrap() {
        return new WebTransportStreamChannelBootstrap(this);
    }

    @Override
    public void drain() {
        ByteBuf buf = sessionChannel.alloc().buffer();
        Capsule.encode(new Capsule.DrainSession(), buf);
        sessionChannel.writeAndFlush(new DefaultHttp3DataFrame(buf));
    }

    @Override
    public void close(int applicationErrorCode, String applicationErrorMessage) {
        Capsule.CloseSession capsule =
                new Capsule.CloseSession(applicationErrorCode, applicationErrorMessage);
        ByteBuf buf = sessionChannel.alloc().buffer();
        Capsule.encode(capsule, buf);
        sessionChannel
                .writeAndFlush(new DefaultHttp3DataFrame(buf))
                .addListener(f -> sessionChannel.shutdownOutput());
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
