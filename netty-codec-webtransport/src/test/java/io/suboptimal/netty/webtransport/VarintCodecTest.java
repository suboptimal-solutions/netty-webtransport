package io.suboptimal.netty.webtransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VarintCodecTest {

    // RFC 9000 §A.1 test vectors
    @ParameterizedTest
    @CsvSource({
        "0x25,                  37",
        "0x6329,                9001",
        "0x9d7f3e7d,            494878333",
        "0xc2197c5eff14e88c,    151288809941952652",
    })
    void rfc9000TestVectors(String hexStr, long expected) {
        byte[] bytes = hexToBytes(hexStr);
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        long value = VarintCodec.readVarint(buf);
        assertThat(value).isEqualTo(expected);
        assertThat(buf.readableBytes()).isZero();
        buf.release();
    }

    @ParameterizedTest
    @CsvSource({
        "0,   1",
        "63,  1",
        "64,  2",
        "16383, 2",
        "16384, 4",
        "1073741823, 4",
        "1073741824, 8",
        "4611686018427387903, 8",
    })
    void encodedLength(long value, int expectedLength) {
        assertThat(VarintCodec.encodedLength(value)).isEqualTo(expectedLength);
    }

    @Test
    void roundTrip() {
        long[] values = {0, 1, 63, 64, 16383, 16384, 1073741823, 1073741824, 4611686018427387903L};
        ByteBuf buf = Unpooled.buffer();
        for (long v : values) {
            VarintCodec.writeVarint(buf, v);
        }
        for (long v : values) {
            assertThat(VarintCodec.readVarint(buf)).isEqualTo(v);
        }
        assertThat(buf.readableBytes()).isZero();
        buf.release();
    }

    @Test
    void peekDoesNotAdvance() {
        ByteBuf buf = Unpooled.buffer();
        VarintCodec.writeVarint(buf, 42);
        int before = buf.readerIndex();
        long peeked = VarintCodec.peekVarint(buf);
        assertThat(peeked).isEqualTo(42);
        assertThat(buf.readerIndex()).isEqualTo(before);
        buf.release();
    }

    @Test
    void isReadablePartialVarint() {
        ByteBuf buf = Unpooled.buffer();
        VarintCodec.writeVarint(buf, 9001);
        ByteBuf partial = buf.readSlice(1);
        assertThat(VarintCodec.isReadable(partial)).isFalse();
        buf.release();
    }

    @Test
    void isReadableEmpty() {
        ByteBuf buf = Unpooled.EMPTY_BUFFER;
        assertThat(VarintCodec.isReadable(buf)).isFalse();
    }

    @Test
    void isReadableComplete() {
        ByteBuf buf = Unpooled.buffer();
        VarintCodec.writeVarint(buf, 42);
        assertThat(VarintCodec.isReadable(buf)).isTrue();
        buf.release();
    }

    @Test
    void rejectsOverflow() {
        assertThatThrownBy(() -> VarintCodec.encodedLength(VarintCodec.MAX_VALUE + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void varintLengthFromFirstByte() {
        assertThat(VarintCodec.varintLength(0x00)).isEqualTo(1);
        assertThat(VarintCodec.varintLength(0x3F)).isEqualTo(1);
        assertThat(VarintCodec.varintLength(0x40)).isEqualTo(2);
        assertThat(VarintCodec.varintLength(0x7F)).isEqualTo(2);
        assertThat(VarintCodec.varintLength(0x80)).isEqualTo(4);
        assertThat(VarintCodec.varintLength(0xBF)).isEqualTo(4);
        assertThat(VarintCodec.varintLength(0xC0)).isEqualTo(8);
        assertThat(VarintCodec.varintLength(0xFF)).isEqualTo(8);
    }

    private static byte[] hexToBytes(String hex) {
        hex = hex.startsWith("0x") ? hex.substring(2) : hex;
        if (hex.length() % 2 != 0) hex = "0" + hex;
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
