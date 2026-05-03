package io.suboptimal.netty.webtransport;

import io.netty.buffer.ByteBuf;

/**
 * Zero-allocation QUIC variable-length integer codec operating directly on {@link ByteBuf}.
 *
 * <p>RFC 9000 §16.
 */
public final class VarintCodec {

    static final long MAX_VALUE = (1L << 62) - 1;

    private VarintCodec() {}

    public static int varintLength(int firstByte) {
        return 1 << ((firstByte & 0xC0) >> 6);
    }

    public static int encodedLength(long value) {
        if (value <= 63) return 1;
        if (value <= 16383) return 2;
        if (value <= 1073741823) return 4;
        if (value <= MAX_VALUE) return 8;
        throw new IllegalArgumentException("Value exceeds maximum varint: " + value);
    }

    public static long readVarint(ByteBuf buf) {
        int firstByte = buf.readUnsignedByte();
        int length = 1 << ((firstByte & 0xC0) >> 6);
        long value = firstByte & 0x3F;
        for (int i = 1; i < length; i++) {
            value = (value << 8) | buf.readUnsignedByte();
        }
        return value;
    }

    public static long peekVarint(ByteBuf buf) {
        int readerIndex = buf.readerIndex();
        long value = readVarint(buf);
        buf.readerIndex(readerIndex);
        return value;
    }

    public static boolean isReadable(ByteBuf buf) {
        if (buf.readableBytes() < 1) return false;
        int length = varintLength(buf.getUnsignedByte(buf.readerIndex()));
        return buf.readableBytes() >= length;
    }

    public static void writeVarint(ByteBuf buf, long value) {
        int len = encodedLength(value);
        switch (len) {
            case 1 -> buf.writeByte((int) value);
            case 2 -> buf.writeShort((int) (value | 0x4000));
            case 4 -> buf.writeInt((int) (value | 0x80000000L));
            case 8 -> buf.writeLong(value | 0xC000000000000000L);
            default -> throw new IllegalArgumentException("Value exceeds maximum varint: " + value);
        }
    }
}
