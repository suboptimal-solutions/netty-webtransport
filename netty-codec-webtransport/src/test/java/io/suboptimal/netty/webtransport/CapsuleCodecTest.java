package io.suboptimal.netty.webtransport;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CapsuleCodecTest {

    private CapsuleCodec codec;

    @BeforeEach
    void setUp() {
        codec = new CapsuleCodec(UnpooledByteBufAllocator.DEFAULT);
    }

    @AfterEach
    void tearDown() {
        codec.release();
    }

    @Test
    void decodesCompleteCapsule() {
        ByteBuf data = UnpooledByteBufAllocator.DEFAULT.buffer();
        Capsule.encode(new Capsule.DrainSession(), data);

        List<Capsule> capsules = codec.decode(data);
        assertThat(capsules).hasSize(1);
        assertThat(capsules.getFirst()).isEqualTo(new Capsule.DrainSession());
        data.release();
    }

    @Test
    void handlesPartialCapsule() {
        ByteBuf full = UnpooledByteBufAllocator.DEFAULT.buffer();
        Capsule.encode(new Capsule.MaxData(42), full);

        ByteBuf part1 = full.readRetainedSlice(1);
        ByteBuf part2 = full.readRetainedSlice(full.readableBytes());
        full.release();

        List<Capsule> first = codec.decode(part1);
        assertThat(first).isEmpty();

        List<Capsule> second = codec.decode(part2);
        assertThat(second).hasSize(1);
        assertThat(second.getFirst()).isEqualTo(new Capsule.MaxData(42));

        part1.release();
        part2.release();
    }

    @Test
    void decodesMultipleCapsulesInOneBuf() {
        ByteBuf data = UnpooledByteBufAllocator.DEFAULT.buffer();
        Capsule.encode(new Capsule.MaxStreamsBidi(10), data);
        Capsule.encode(new Capsule.MaxStreamsUni(20), data);

        List<Capsule> capsules = codec.decode(data);
        assertThat(capsules).hasSize(2);
        assertThat(capsules.get(0)).isEqualTo(new Capsule.MaxStreamsBidi(10));
        assertThat(capsules.get(1)).isEqualTo(new Capsule.MaxStreamsUni(20));
        data.release();
    }

    @Test
    void decodesCloseSession() {
        ByteBuf data = UnpooledByteBufAllocator.DEFAULT.buffer();
        Capsule.encode(new Capsule.CloseSession(1, "error"), data);

        List<Capsule> capsules = codec.decode(data);
        assertThat(capsules).hasSize(1);
        assertThat(capsules.getFirst()).isEqualTo(new Capsule.CloseSession(1, "error"));
        data.release();
    }
}
