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

- **Capsule protocol parser (RFC 9297).** Reads / writes capsules off the
  HTTP/3 DATA frames on the CONNECT stream. The capsule type registry is
  open-ended; we implement the WebTransport-relevant ones (DRAIN_SESSION,
  CLOSE_SESSION, MAX_STREAMS, etc.).
- **WebTransport session registry.** Maps `(connection, session-id)` tuples
  to a session object. Lookups happen on every datagram and every
  client-initiated stream, so it is on the hot path.
- **Datagram context-id demux.** RFC 9297 datagrams arrive at `QuicChannel`
  with a varint context-id (the quarter stream ID, per
  `draft-ietf-webtrans-http3-15`). We demux on this id to route to the right
  session.
- **WT_STREAM frame on request streams.** WebTransport streams are HTTP/3
  request streams whose first frame is `WT_STREAM`. The HTTP/3 codec exposes
  unknown frames; we recognize and unwrap them.
- **Unidirectional stream-type reader.** Server-initiated unidirectional
  streams begin with a varint type byte (`0x54` = WebTransport). We dispatch
  on it before handing the stream to the session.
- **`SETTINGS_ENABLE_CONNECT_PROTOCOL` and `SETTINGS_H3_DATAGRAM` plumbing.**
  Netty's `Http3SettingsFrame` is a generic name→value map; we surface the
  WebTransport-relevant settings and validate them on connect.
- **`SETTINGS_WT_MAX_SESSIONS` advertisement and enforcement.**

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
