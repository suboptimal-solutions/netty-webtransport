package io.suboptimal.netty.webtransport;

import static io.suboptimal.netty.webtransport.WebTransportProtocol.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Sealed hierarchy of WebTransport capsule types.
 *
 * <p>RFC 9297 §3.2 (capsule wire format), draft-ietf-webtrans-http3-15 §6 and §5.6.
 */
public sealed interface Capsule {

    // --- Session lifecycle (§6, §4.7) ---

    record CloseSession(int applicationErrorCode, String applicationErrorMessage) implements Capsule {
        public CloseSession {
            if (applicationErrorMessage.length() > 1024) {
                throw new IllegalArgumentException("Error message exceeds 1024 bytes");
            }
        }
    }

    record DrainSession() implements Capsule {}

    // --- Flow control (§5.6) ---

    record MaxStreamsBidi(long maximumStreams) implements Capsule {}

    record MaxStreamsUni(long maximumStreams) implements Capsule {}

    record StreamsBlockedBidi(long maximumStreams) implements Capsule {}

    record StreamsBlockedUni(long maximumStreams) implements Capsule {}

    record MaxData(long maximumData) implements Capsule {}

    record DataBlocked(long maximumData) implements Capsule {}

    // --- Unknown capsule (for forward compatibility, RFC 9297 §3.2) ---

    record Unknown(long type, ByteBuf payload) implements Capsule {}

    // --- Encoding ---

    static void encode(Capsule capsule, ByteBuf out) {
        switch (capsule) {
            case CloseSession c -> {
                byte[] msgBytes = c.applicationErrorMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                long payloadLen = 4L + msgBytes.length;
                VarintCodec.writeVarint(out, CAPSULE_CLOSE_SESSION);
                VarintCodec.writeVarint(out, payloadLen);
                out.writeInt(c.applicationErrorCode());
                out.writeBytes(msgBytes);
            }
            case DrainSession ignored -> {
                VarintCodec.writeVarint(out, CAPSULE_DRAIN_SESSION);
                VarintCodec.writeVarint(out, 0);
            }
            case MaxStreamsBidi m -> {
                VarintCodec.writeVarint(out, CAPSULE_MAX_STREAMS_BIDI);
                VarintCodec.writeVarint(out, VarintCodec.encodedLength(m.maximumStreams()));
                VarintCodec.writeVarint(out, m.maximumStreams());
            }
            case MaxStreamsUni m -> {
                VarintCodec.writeVarint(out, CAPSULE_MAX_STREAMS_UNI);
                VarintCodec.writeVarint(out, VarintCodec.encodedLength(m.maximumStreams()));
                VarintCodec.writeVarint(out, m.maximumStreams());
            }
            case StreamsBlockedBidi s -> {
                VarintCodec.writeVarint(out, CAPSULE_STREAMS_BLOCKED_BIDI);
                VarintCodec.writeVarint(out, VarintCodec.encodedLength(s.maximumStreams()));
                VarintCodec.writeVarint(out, s.maximumStreams());
            }
            case StreamsBlockedUni s -> {
                VarintCodec.writeVarint(out, CAPSULE_STREAMS_BLOCKED_UNI);
                VarintCodec.writeVarint(out, VarintCodec.encodedLength(s.maximumStreams()));
                VarintCodec.writeVarint(out, s.maximumStreams());
            }
            case MaxData m -> {
                VarintCodec.writeVarint(out, CAPSULE_MAX_DATA);
                VarintCodec.writeVarint(out, VarintCodec.encodedLength(m.maximumData()));
                VarintCodec.writeVarint(out, m.maximumData());
            }
            case DataBlocked d -> {
                VarintCodec.writeVarint(out, CAPSULE_DATA_BLOCKED);
                VarintCodec.writeVarint(out, VarintCodec.encodedLength(d.maximumData()));
                VarintCodec.writeVarint(out, d.maximumData());
            }
            case Unknown u -> {
                VarintCodec.writeVarint(out, u.type());
                VarintCodec.writeVarint(out, u.payload().readableBytes());
                out.writeBytes(u.payload(), u.payload().readerIndex(), u.payload().readableBytes());
            }
        }
    }

    // --- Decoding (stateful, call from CapsuleDecoder) ---

    static Capsule decode(long type, ByteBuf payload) {
        if (type == CAPSULE_CLOSE_SESSION) {
            int errorCode = payload.readInt();
            int msgLen = payload.readableBytes();
            String msg =
                    msgLen > 0
                            ? payload.readCharSequence(msgLen, java.nio.charset.StandardCharsets.UTF_8).toString()
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
            return new Unknown(type, payload.readableBytes() > 0 ? payload.readRetainedSlice(payload.readableBytes()) : Unpooled.EMPTY_BUFFER);
        }
    }
}
