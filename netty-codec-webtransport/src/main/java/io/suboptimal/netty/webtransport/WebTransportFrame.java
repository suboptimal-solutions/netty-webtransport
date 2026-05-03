package io.suboptimal.netty.webtransport;

/**
 * Marker for objects that flow through a WebTransport pipeline as messages (as opposed to
 * lifecycle {@link WebTransportSessionEvent} / {@link WebTransportStreamEvent} user events).
 *
 * <p>Currently {@link WebTransportDatagramFrame} is the only member. Stream payload bytes arrive
 * as plain {@link io.netty.buffer.ByteBuf}s on the per-stream channel — there is intentionally no
 * stream-data frame wrapper.
 */
public sealed interface WebTransportFrame permits WebTransportDatagramFrame {}
