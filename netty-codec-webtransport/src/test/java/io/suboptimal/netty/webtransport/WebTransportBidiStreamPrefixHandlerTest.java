package io.suboptimal.netty.webtransport;

import static io.suboptimal.netty.webtransport.WebTransportProtocol.WT_STREAM_FRAME_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.suboptimal.netty.webtransport.internal.SessionRegistry;
import io.suboptimal.netty.webtransport.internal.WebTransportBidiStreamPrefixHandler;
import org.junit.jupiter.api.Test;

class WebTransportBidiStreamPrefixHandlerTest {

    @Test
    void nonWtStreamFirstVarintFallsBackAndRemovesSelf() {
        SessionRegistry registry = new SessionRegistry();
        WebTransportBidiStreamPrefixHandler handler =
                new WebTransportBidiStreamPrefixHandler(registry, null);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // 0x01 is a valid 1-byte varint that is not WT_STREAM_FRAME_TYPE (0x41).
        ByteBuf buf = Unpooled.wrappedBuffer(new byte[] {0x01, 0x02, 0x03});
        channel.writeInbound(buf);

        // Handler should have removed itself; bytes pass through unchanged.
        assertThat(channel.pipeline().get(WebTransportBidiStreamPrefixHandler.class)).isNull();

        ByteBuf passthrough = channel.readInbound();
        assertThat(passthrough).isNotNull();
        assertThat(passthrough.readableBytes()).isEqualTo(3);
        passthrough.release();

        assertThat(channel.finishAndReleaseAll()).isFalse();
    }

    @Test
    void wtStreamWithUnknownSessionIdClosesChannel() {
        SessionRegistry registry = new SessionRegistry();
        WebTransportBidiStreamPrefixHandler handler =
                new WebTransportBidiStreamPrefixHandler(registry, null);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        ByteBuf buf = channel.alloc().buffer();
        VarintCodec.writeVarint(buf, WT_STREAM_FRAME_TYPE);
        VarintCodec.writeVarint(buf, 999L); // Not in registry.
        buf.writeBytes(new byte[] {1, 2, 3});

        channel.writeInbound(buf);

        assertThat(channel.isOpen()).isFalse();
        channel.finishAndReleaseAll();
    }

    @Test
    void waitsForFullSessionIdVarint() {
        SessionRegistry registry = new SessionRegistry();
        WebTransportBidiStreamPrefixHandler handler =
                new WebTransportBidiStreamPrefixHandler(registry, null);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Only the WT_STREAM_FRAME_TYPE varint, no session ID yet.
        ByteBuf partial = channel.alloc().buffer();
        VarintCodec.writeVarint(partial, WT_STREAM_FRAME_TYPE);
        channel.writeInbound(partial);

        assertThat(channel.pipeline().get(WebTransportBidiStreamPrefixHandler.class)).isNotNull();
        assertThat(channel.<Object>readInbound()).isNull();
        assertThat(channel.isOpen()).isTrue();

        channel.finishAndReleaseAll();
    }
}
