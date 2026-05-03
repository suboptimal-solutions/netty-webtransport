package io.suboptimal.netty.webtransport;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.suboptimal.netty.webtransport.internal.SessionRegistry;
import io.suboptimal.netty.webtransport.internal.WebTransportUniStreamPrefixHandler;
import org.junit.jupiter.api.Test;

class WebTransportUniStreamPrefixHandlerTest {

    @Test
    void unknownSessionIdClosesChannel() {
        SessionRegistry registry = new SessionRegistry();
        WebTransportUniStreamPrefixHandler handler =
                new WebTransportUniStreamPrefixHandler(registry, null);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        ByteBuf buf = channel.alloc().buffer();
        VarintCodec.writeVarint(buf, 42L); // unknown session ID
        buf.writeBytes(new byte[] {7, 8, 9});

        channel.writeInbound(buf);

        assertThat(channel.isOpen()).isFalse();
        channel.finishAndReleaseAll();
    }

    @Test
    void waitsForFullSessionIdVarint() {
        SessionRegistry registry = new SessionRegistry();
        WebTransportUniStreamPrefixHandler handler =
                new WebTransportUniStreamPrefixHandler(registry, null);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Empty buffer — no varint readable.
        channel.writeInbound(channel.alloc().buffer());

        assertThat(channel.pipeline().get(WebTransportUniStreamPrefixHandler.class)).isNotNull();
        assertThat(channel.isOpen()).isTrue();

        channel.finishAndReleaseAll();
    }
}
