# Netty stack

What we depend on, why, and what we have to build on top.

## Latest Netty

We target **Netty 4.2.12.Final** (released 2026-03-25). Both `netty-codec-http3`
and `netty-codec-quic` graduated from incubator into the main `io.netty` group
in **4.2.1.Final** (May 2025). All artifacts we depend on live under
groupId `io.netty`; we do **not** depend on any incubator coordinates.

Sources:

- Maven Central: `https://repo.maven.apache.org/maven2/io/netty/netty-codec-http3/maven-metadata.xml`
- Release notes: <https://netty.io/news/2025/05/06/4-2-1.html>

## Artifacts we depend on

| GAV | Why |
| --- | --- |
| `io.netty:netty-codec-http3:4.2.12.Final` | HTTP/3 codec — handlers, frame types, SETTINGS, control stream, request streams. Transitively pulls `netty-codec-quic`, `netty-buffer`, `netty-common`, `netty-transport`. |
| `io.netty:netty-codec-quic:4.2.12.Final` | QUIC API: `QuicChannel`, `QuicStreamChannel`, `QuicSslContext`, transport-parameter and datagram support. Pulled in transitively via `netty-codec-http3`. |
| `io.netty:netty-codec-native-quic:4.2.12.Final` (with classifier) | Native bindings: Cloudflare quiche via JNI + BoringSSL. Runtime scope. Classifier resolved at build time by `os-maven-plugin`. |
| `org.junit.jupiter:junit-jupiter` (BOM-managed) | Test framework. |
| `org.assertj:assertj-core` | Test assertions. |

## Native classifiers

`netty-codec-native-quic` ships per-platform JARs containing the compiled
`.so` / `.dylib` / `.dll`. The build pulls the right one via
`${os.detected.classifier}` (populated by `kr.motd.maven:os-maven-plugin` in
the parent POM):

| Classifier | Platform |
| --- | --- |
| `linux-x86_64` | Linux on x86_64 (the common Docker / CI target) |
| `linux-aarch_64` | Linux on ARM64 (Graviton, modern ARM servers) |
| `osx-x86_64` | macOS on Intel |
| `osx-aarch_64` | macOS on Apple Silicon |
| `windows-x86_64` | Windows on x86_64 |

Android ABIs (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) are also published
upstream but we do not test against them.

## JDK requirements

- **Library:** Java 21 LTS for our own code (parent POM enforces via
  `maven-enforcer-plugin`).
- **Netty 4.2 itself:** ships Java 11 bytecode. The reason we require 21 is
  for our own use of records, sealed interfaces, and pattern matching — not a
  Netty constraint.

## Gaps Netty does not fill (we implement these)

Netty's HTTP/3 codec stops at HTTP/3 framing; WebTransport semantics live on
top. The codec module owes:

- **Capsule protocol parser (RFC 9297).** ✅ Implemented — `Capsule` (sealed)
  + `CapsuleCodec`. Reads / writes capsules off the HTTP/3 DATA frames on the
  CONNECT stream. We cover `CLOSE_WEBTRANSPORT_SESSION`,
  `DRAIN_WEBTRANSPORT_SESSION`, `WT_MAX_STREAMS_{BIDI,UNI}`, `WT_MAX_DATA`,
  `WT_STREAMS_BLOCKED_{BIDI,UNI}`, `WT_DATA_BLOCKED`, plus a forward-compat
  `Capsule.Unknown`.
- **WebTransport session registry.** ✅ Implemented — `internal.SessionRegistry`,
  a `ConcurrentHashMap<Long, DefaultWebTransportSession>`. Hot-path O(1)
  lookups for every datagram and every peer-initiated stream.
- **Datagram context-id demux.** ✅ Implemented —
  `internal.WebTransportDatagramRouter` (parent QuicChannel pipeline) +
  `internal.WebTransportSessionDatagramOutboundHandler` (session channel
  pipeline, outbound). RFC 9297 §2.1 + draft-15 §4.5.
- **WT_STREAM signal at the start of WebTransport bidi streams.** ✅
  Implemented — `internal.WebTransportBidiStreamPrefixHandler`. Strips the
  signal value `0x41` and session ID before HTTP/3 framing applies; falls back
  to HTTP/3 if the leading varint is anything else.
- **Unidirectional stream-type reader.** ✅ Implemented —
  `internal.WebTransportUniStreamPrefixHandler` (the `0x54` stream type byte
  is consumed by the HTTP/3 layer's unknown-stream-type factory; the prefix
  handler then reads the session-ID varint).
- **`SETTINGS_ENABLE_CONNECT_PROTOCOL` and `SETTINGS_H3_DATAGRAM` plumbing.**
  ✅ Implemented in `WebTransportServerProtocolHandler.buildSettings()`
  (uses `Http3Settings.enableConnectProtocol(true)` /
  `enableH3Datagram(true)`).
- **`SETTINGS_WT_INITIAL_MAX_STREAMS_*` and `SETTINGS_WT_INITIAL_MAX_DATA`
  advertisement.** ✅ Implemented in the same builder. Enforcement against
  these limits on outbound streams / data is **not yet implemented** — the
  capsule values are decoded onto the session, but
  `WebTransportStreamChannelBootstrap` does not yet consult them. Tracked
  under Phase 6 of the [roadmap](roadmap.md).

For the full spec-to-class map see [wire-format.md](wire-format.md).

## Version compatibility matrix

| `netty-codec-webtransport` | Netty | JDK | Tested OS | Vendored quiche¹ |
| --- | --- | --- | --- | --- |
| 0.0.x (current) | 4.2.12.Final | 21+ | linux-x86_64 (CI) | Whatever ships in `netty-codec-native-quic:4.2.12.Final` |

¹ The quiche version is determined by Netty's release; we don't pin it
independently. Surface from `netty-codec-native-quic` release notes when
upgrading.

Update this table in the same commit that bumps Netty.

## Superseded artifacts (do NOT depend on)

These older incubator coordinates still exist on Maven Central but do not
receive new releases:

- `io.netty.incubator:netty-incubator-codec-quic`
- `io.netty.incubator:netty-incubator-codec-classes-quic`
- `io.netty.incubator:netty-incubator-codec-native-quic`
- `io.netty.incubator:netty-incubator-codec-http3`

If a PR adds an incubator dep, reject it.
