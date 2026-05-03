package io.suboptimal.netty.webtransport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.DefaultByteBufHolder;

/**
 * A WebTransport datagram (RFC 9297 §2.1, draft-ietf-webtrans-http3-15 §4.5).
 *
 * <p>Inbound: arrives as a {@code channelRead} on the session channel after the
 * quarter-stream-id prefix has been stripped. Outbound: write one to the session channel and the
 * internal session handler will prepend the prefix before forwarding to the parent QUIC channel.
 *
 * <p>Refcount semantics follow {@link DefaultByteBufHolder}: {@link #content()} returns a slice
 * sharing storage with the wire buffer; the receiver owns the reference and must release (or pass
 * to a writer that consumes it).
 */
public final class WebTransportDatagramFrame extends DefaultByteBufHolder
        implements WebTransportFrame {

    public WebTransportDatagramFrame(ByteBuf content) {
        super(content);
    }

    @Override
    public WebTransportDatagramFrame copy() {
        return new WebTransportDatagramFrame(content().copy());
    }

    @Override
    public WebTransportDatagramFrame duplicate() {
        return new WebTransportDatagramFrame(content().duplicate());
    }

    @Override
    public WebTransportDatagramFrame retainedDuplicate() {
        return new WebTransportDatagramFrame(content().retainedDuplicate());
    }

    @Override
    public WebTransportDatagramFrame replace(ByteBuf content) {
        return new WebTransportDatagramFrame(content);
    }

    @Override
    public WebTransportDatagramFrame retain() {
        super.retain();
        return this;
    }

    @Override
    public WebTransportDatagramFrame retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public WebTransportDatagramFrame touch() {
        super.touch();
        return this;
    }

    @Override
    public WebTransportDatagramFrame touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ByteBufHolder && super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
