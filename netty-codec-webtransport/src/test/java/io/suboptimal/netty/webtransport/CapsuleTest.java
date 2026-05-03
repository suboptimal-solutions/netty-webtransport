package io.suboptimal.netty.webtransport;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class CapsuleTest {

    @Test
    void closeSessionRoundTrip() {
        Capsule.CloseSession original = new Capsule.CloseSession(42, "goodbye");
        ByteBuf buf = Unpooled.buffer();
        Capsule.encode(original, buf);
        long type = VarintCodec.readVarint(buf);
        long length = VarintCodec.readVarint(buf);
        ByteBuf payload = buf.readSlice((int) length);
        Capsule decoded = Capsule.decode(type, payload);
        assertThat(decoded).isEqualTo(original);
        buf.release();
    }

    @Test
    void drainSessionRoundTrip() {
        Capsule.DrainSession original = new Capsule.DrainSession();
        ByteBuf buf = Unpooled.buffer();
        Capsule.encode(original, buf);
        long type = VarintCodec.readVarint(buf);
        long length = VarintCodec.readVarint(buf);
        assertThat(length).isZero();
        Capsule decoded = Capsule.decode(type, buf.readSlice((int) length));
        assertThat(decoded).isEqualTo(original);
        buf.release();
    }

    @Test
    void maxStreamsBidiRoundTrip() {
        Capsule.MaxStreamsBidi original = new Capsule.MaxStreamsBidi(100);
        assertRoundTrip(original);
    }

    @Test
    void maxStreamsUniRoundTrip() {
        Capsule.MaxStreamsUni original = new Capsule.MaxStreamsUni(200);
        assertRoundTrip(original);
    }

    @Test
    void streamsBlockedBidiRoundTrip() {
        Capsule.StreamsBlockedBidi original = new Capsule.StreamsBlockedBidi(50);
        assertRoundTrip(original);
    }

    @Test
    void streamsBlockedUniRoundTrip() {
        Capsule.StreamsBlockedUni original = new Capsule.StreamsBlockedUni(75);
        assertRoundTrip(original);
    }

    @Test
    void maxDataRoundTrip() {
        Capsule.MaxData original = new Capsule.MaxData(1_000_000);
        assertRoundTrip(original);
    }

    @Test
    void dataBlockedRoundTrip() {
        Capsule.DataBlocked original = new Capsule.DataBlocked(500_000);
        assertRoundTrip(original);
    }

    @Test
    void unknownCapsuleIsPreserved() {
        ByteBuf payload = Unpooled.wrappedBuffer(new byte[] {1, 2, 3});
        Capsule.Unknown original = new Capsule.Unknown(0xFFFF, payload);
        ByteBuf buf = Unpooled.buffer();
        Capsule.encode(original, buf);
        long type = VarintCodec.readVarint(buf);
        long length = VarintCodec.readVarint(buf);
        assertThat(type).isEqualTo(0xFFFF);
        assertThat(length).isEqualTo(3);
        Capsule decoded = Capsule.decode(type, buf.readSlice((int) length));
        assertThat(decoded).isInstanceOf(Capsule.Unknown.class);
        Capsule.Unknown u = (Capsule.Unknown) decoded;
        assertThat(u.type()).isEqualTo(0xFFFF);
        assertThat(u.payload().readableBytes()).isEqualTo(3);
        u.payload().release();
        payload.release();
        buf.release();
    }

    @Test
    void closeSessionEmptyMessage() {
        Capsule.CloseSession original = new Capsule.CloseSession(0, "");
        assertRoundTrip(original);
    }

    @Test
    void multipleCapsulesSerialized() {
        ByteBuf buf = Unpooled.buffer();
        Capsule.encode(new Capsule.MaxStreamsBidi(10), buf);
        Capsule.encode(new Capsule.MaxData(1000), buf);
        Capsule.encode(new Capsule.DrainSession(), buf);

        Capsule c1 = decodeSingle(buf);
        Capsule c2 = decodeSingle(buf);
        Capsule c3 = decodeSingle(buf);

        assertThat(c1).isEqualTo(new Capsule.MaxStreamsBidi(10));
        assertThat(c2).isEqualTo(new Capsule.MaxData(1000));
        assertThat(c3).isEqualTo(new Capsule.DrainSession());
        assertThat(buf.readableBytes()).isZero();
        buf.release();
    }

    private void assertRoundTrip(Capsule original) {
        ByteBuf buf = Unpooled.buffer();
        Capsule.encode(original, buf);
        Capsule decoded = decodeSingle(buf);
        assertThat(decoded).isEqualTo(original);
        assertThat(buf.readableBytes()).isZero();
        buf.release();
    }

    private Capsule decodeSingle(ByteBuf buf) {
        long type = VarintCodec.readVarint(buf);
        long length = VarintCodec.readVarint(buf);
        return Capsule.decode(type, buf.readSlice((int) length));
    }
}
