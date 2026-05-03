package io.suboptimal.netty.webtransport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateful capsule decoder that accumulates DATA frame payloads and emits {@link Capsule} objects.
 *
 * <p>RFC 9297 §3.2 — capsule wire format: type(varint) + length(varint) + value(bytes).
 *
 * <p>Not a {@code ByteToMessageDecoder} because capsule bytes arrive inside HTTP/3 DATA frames, not
 * raw on the stream. The owning handler feeds DATA frame content via {@link #decode}.
 */
public final class CapsuleCodec {

    private CompositeByteBuf cumulation;
    private final ByteBufAllocator alloc;

    public CapsuleCodec(ByteBufAllocator alloc) {
        this.alloc = alloc;
    }

    public List<Capsule> decode(ByteBuf data) {
        List<Capsule> out = new ArrayList<>(4);
        if (cumulation == null) {
            cumulation = alloc.compositeBuffer();
        }
        cumulation.addComponent(true, data.retain());
        while (decodeCapsule(cumulation, out)) {
            // keep decoding
        }
        discardReadComponents();
        return out;
    }

    private boolean decodeCapsule(ByteBuf buf, List<Capsule> out) {
        if (!VarintCodec.isReadable(buf)) return false;

        buf.markReaderIndex();
        long type = VarintCodec.readVarint(buf);

        if (!VarintCodec.isReadable(buf)) {
            buf.resetReaderIndex();
            return false;
        }
        long length = VarintCodec.readVarint(buf);

        if (buf.readableBytes() < length) {
            buf.resetReaderIndex();
            return false;
        }

        ByteBuf payload = buf.readSlice((int) length);
        out.add(Capsule.decode(type, payload));
        return true;
    }

    private void discardReadComponents() {
        if (cumulation != null && cumulation.readerIndex() > 0) {
            cumulation.discardReadComponents();
        }
    }

    public void release() {
        if (cumulation != null) {
            cumulation.release();
            cumulation = null;
        }
    }
}
