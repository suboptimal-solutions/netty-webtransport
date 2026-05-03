# Project layout

How the repository is organized and why.

## Module shape

Two Maven modules under one parent reactor:

| Directory | Artifact | Role |
| --- | --- | --- |
| [`netty-codec-webtransport/`](../netty-codec-webtransport/) | `netty-codec-webtransport` | The codec — frames, capsules, handlers, server, session API. |
| [`netty-codec-webtransport-example/`](../netty-codec-webtransport-example/) | `netty-codec-webtransport-example` | Runnable demos. Hosts `EchoServer`, `EchoSessionHandler`, `EchoStreamHandler`. |

The single-module-codec shape mirrors how `netty-codec-http3`,
`netty-codec-http2`, `netty-codec-quic`, and `netty-codec-mqtt` ship in Netty
proper: one artifact contains frame parsing, codec utilities, **and** handlers,
because separating "frames" from "handlers" creates artificial boundaries when
both layers evolve together.

The example module exists for one reason only: demos shouldn't be on the
production classpath of the main artifact.

## Module directory naming

Module directories match their `artifactId` exactly — no abbreviated
`codec/` / `example/` directories. This keeps `mvn -pl netty-codec-webtransport
...` invocations unambiguous and matches what the main Netty repo does.

## Tests

Unit and integration tests both live in
`netty-codec-webtransport/src/test/java`. If integration tests later need
special JVM args (e.g. `--add-opens` for Netty internals on JDK 21+) or
longer timeouts, gate them with a Maven `integration` profile rather than
splitting modules.

## Java package conventions

| Package | Contents |
| --- | --- |
| `io.suboptimal.netty.webtransport` | Public API: `WebTransportServerProtocolHandler`, `WebTransportSession`, the three abstract `*Initializer` classes (session / bidi stream / uni stream), `WebTransportStreamChannelBootstrap`, the sealed `WebTransportSessionEvent` / `WebTransportStreamEvent` user-event hierarchies, the `WebTransportFrame` / `WebTransportDatagramFrame` message hierarchy, and the codec primitives `VarintCodec` / `Capsule` / `CapsuleCodec` / `WebTransportProtocol`. |
| `io.suboptimal.netty.webtransport.internal.*` | Implementation: the connect handler, steady-state session inbound handler, datagram outbound interceptor, bidi/uni stream prefix handlers, datagram router, session registry, default session implementation. User code should not reference these. |
| `io.suboptimal.netty.webtransport.example` | Demo handlers in the example module: `EchoServer`, `EchoSessionHandler`, `EchoStreamHandler`. |

`Automatic-Module-Name: io.suboptimal.netty.webtransport` is set in the codec
JAR manifest for JPMS compatibility.

## Licensing

Apache License 2.0 lives in the root `LICENSE` file only. **No per-file
license headers.** The repository is unambiguously licensed at the root,
which is sufficient under Apache 2.0 §4(b) for distributing as a unit.
Spotless is configured **not** to inject headers, and reviewers reject PRs
that add them.

## Formatting

Spotless + Google Java Format. The contract:

```sh
mvn spotless:apply     # auto-fix Java sources
mvn -B verify          # includes spotless:check; CI fails on bad format
```

`.editorconfig` covers non-Java text files (line endings, trailing whitespace,
final newline, line length). The vendored specs in `specs/` are exempt from
both — they are byte-exact upstream copies.

## Build invocation

Use your locally installed `mvn` (3.9 or newer). The repository deliberately
**does not ship a Maven wrapper** (`mvnw`, `.mvn/wrapper/`). Reasons:

- One fewer file family to maintain.
- The `maven-enforcer-plugin` rule on the parent POM gives a clear error
  message when the local install is too old.
- Most JDK / Maven environments are managed by package managers or SDKMAN!.

If you need to install Maven, see <https://maven.apache.org/install.html>.

## Versioning

`0.0.X-SNAPSHOT` while pre-1.0. No `.Final` suffix — that's a JBoss / Netty
convention we're not adopting. Bumping rules:

- **Patch** (`0.0.1` → `0.0.2`) for bug fixes and small additions.
- **Minor** (`0.0.x` → `0.1.0`) when a roadmap phase lands (see
  [roadmap.md](roadmap.md)).
- **Major** (`0.x.y` → `1.0.0`) when the API stabilizes and we have browser
  interop. `1.0.0` will be `1.0.0-SNAPSHOT` → `1.0.0`.
