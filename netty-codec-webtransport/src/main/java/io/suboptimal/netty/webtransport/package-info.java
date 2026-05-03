/**
 * WebTransport-over-HTTP/3 codec, handlers, and session API for Netty.
 *
 * <p>Implements the wire protocol defined in {@code draft-ietf-webtrans-http3} on top of {@code
 * io.netty:netty-codec-http3} and {@code io.netty:netty-codec-quic}. Pre-1.0; the API surface is
 * unstable and may change between minor versions.
 *
 * <p>The vendored specifications, with sha256 sums and fetch dates, live in the {@code specs/}
 * directory at the repository root. See {@code docs/architecture.md} for design rules — in
 * particular zero-copy data-path requirements, threading model, and reference-counting discipline —
 * that all classes in this package must follow.
 */
package io.suboptimal.netty.webtransport;
