package io.suboptimal.netty.webtransport;

import static io.suboptimal.netty.webtransport.WebTransportProtocol.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;

/**
 * Sealed hierarchy of WebTransport capsule types.
 *
 * <p>RFC 9297 §3.2 (capsule wire format), draft-ietf-webtrans-http3-15 §6 and §5.6.
 */
public sealed interface Capsule {

    record CloseSession(int applicationErrorCode, String applicationErrorMessage) implements Capsule {
        public CloseSession {
            if (applicationErrorMessage.length() > 1024) {
                throw new IllegalArgumentException("Error message exceeds 1024 bytes");
            }
        }
    }

    record DrainSession() implements Capsule {}

    record MaxStreamsBidi(long maximumStreams) implements Capsule {}

    record MaxStreamsUni(long maximumStreams) implements Capsule {}

    record StreamsBlockedBidi(long maximumStreams) implements Capsule {}

    record StreamsBlockedUni(long maximumStreams) implements Capsule {}

    record MaxData(long maximumData) implements Capsule {}

    record DataBlocked(long maximumData) implements Capsule {}

    record Unknown(long type, ByteBuf payload) implements Capsule {}

    static void encode(Capsule capsule, ByteBuf out) {
        switch (capsule) {
            case CloseSession c -> {
                int msgBytes = ByteBufUtil.utf8Bytes(c.applicationErrorMessage());
                VarintCodec.writeVarint(out, CAPSULE_CLOSE_SESSION);
                VarintCodec.writeVarint(out, 4L + msgBytes);
                out.writeInt(c.applicationErrorCode());
                out.writeCharSequence(c.applicationErrorMessage(), StandardCharsets.UTF_8);
            }
            case DrainSession ignored -> {
                VarintCodec.writeVarint(out, CAPSULE_DRAIN_SESSION);
                VarintCodec.writeVarint(out, 0);
            }
            case MaxStreamsBidi m -> writeVarintCapsule(out, CAPSULE_MAX_STREAMS_BIDI, m.maximumStreams());
            case MaxStreamsUni m -> writeVarintCapsule(out, CAPSULE_MAX_STREAMS_UNI, m.maximumStreams());
            case StreamsBlockedBidi s -> writeVarintCapsule(out, CAPSULE_STREAMS_BLOCKED_BIDI, s.maximumStreams());
            case StreamsBlockedUni s -> writeVarintCapsule(out, CAPSULE_STREAMS_BLOCKED_UNI, s.maximumStreams());
            case MaxData m -> writeVarintCapsule(out, CAPSULE_MAX_DATA, m.maximumData());
            case DataBlocked d -> writeVarintCapsule(out, CAPSULE_DATA_BLOCKED, d.maximumData());
            case Unknown u -> {
                VarintCodec.writeVarint(out, u.type());
                VarintCodec.writeVarint(out, u.payload().readableBytes());
                out.writeBytes(u.payload(), u.payload().readerIndex(), u.payload().readableBytes());
            }
        }
    }

    private static void writeVarintCapsule(ByteBuf out, long type, long value) {
        VarintCodec.writeVarint(out, type);
        VarintCodec.writeVarint(out, VarintCodec.encodedLength(value));
        VarintCodec.writeVarint(out, value);
    }

    static Capsule decode(long type, ByteBuf payload) {
        if (type == CAPSULE_CLOSE_SESSION) {
            int errorCode = payload.readInt();
            int msgLen = payload.readableBytes();
            String msg =
                    msgLen > 0
                            ? payload.readCharSequence(msgLen, StandardCharsets.UTF_8).toString()
                            : "";
            return new CloseSession(errorCode, msg);
        } else if (type == CAPSULE_DRAIN_SESSION) {
            return new DrainSession();
        } else if (type == CAPSULE_MAX_STREAMS_BIDI) {
            return new MaxStreamsBidi(VarintCodec.readVarint(payload));
        } else if (type == CAPSULE_MAX_STREAMS_UNI) {
            return new MaxStreamsUni(VarintCodec.readVarint(payload));
        } else if (type == CAPSULE_STREAMS_BLOCKED_BIDI) {
            return new StreamsBlockedBidi(VarintCodec.readVarint(payload));
        } else if (type == CAPSULE_STREAMS_BLOCKED_UNI) {
            return new StreamsBlockedUni(VarintCodec.readVarint(payload));
        } else if (type == CAPSULE_MAX_DATA) {
            return new MaxData(VarintCodec.readVarint(payload));
        } else if (type == CAPSULE_DATA_BLOCKED) {
            return new DataBlocked(VarintCodec.readVarint(payload));
        } else {
            return new Unknown(
                    type,
                    payload.readableBytes() > 0
                            ? payload.readRetainedSlice(payload.readableBytes())
                            : Unpooled.EMPTY_BUFFER);
        }
    }
}
