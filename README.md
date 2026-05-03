# netty-codec-webtransport

WebTransport-over-HTTP/3 server (and, eventually, Java client) built on
[Netty](https://netty.io). Pre-implementation; this repository currently contains
only the project skeleton, vendored specifications, and design documents.

## Status

Pre-1.0 bootstrap. **No protocol code yet.** See [docs/roadmap.md](docs/roadmap.md)
for the phased plan; the priority is a server interoperable with Chrome's
`new WebTransport(...)` JS API.

## Targeting

| Spec | Version |
| --- | --- |
| WebTransport overview | [`draft-ietf-webtrans-overview-12`](specs/draft-ietf-webtrans-overview-12.txt) |
| WebTransport over HTTP/3 (wire protocol) | [`draft-ietf-webtrans-http3-15`](specs/draft-ietf-webtrans-http3-15.txt) |
| WebTransport over HTTP/2 (fallback, future) | [`draft-ietf-webtrans-http2-14`](specs/draft-ietf-webtrans-http2-14.txt) |
| Extended CONNECT | [`RFC 9220`](specs/rfc9220.txt) |
| QUIC datagram | [`RFC 9221`](specs/rfc9221.txt) |
| HTTP datagrams + Capsule Protocol | [`RFC 9297`](specs/rfc9297.txt) |
| W3C WebTransport (browser API) | [`w3c-webtransport.html`](specs/w3c-webtransport.html) |

The exact bytes vendored at bootstrap, with sha256 sums and fetch dates, are
recorded in [specs/README.md](specs/README.md).

## Why another WebTransport implementation?

- **Native to Netty 4.2** — uses the graduated `io.netty:netty-codec-http3` and
  `io.netty:netty-codec-quic` artifacts (in `io.netty` core since 4.2.1.Final,
  May 2025). No incubator coordinates.
- **Zero-copy on data-intensive paths** — direct `ByteBuf`, `slice()` /
  `retainedSlice()` for splits, `CompositeByteBuf` for assembly. No `byte[]` or
  `String` materialization on hot paths. See [docs/architecture.md](docs/architecture.md).
- **Java 21 idioms** — records for immutable frame DTOs, sealed interfaces for
  closed message hierarchies, pattern matching for frame dispatch.

## Requirements

- JDK 21 (LTS)
- Apache Maven 3.9+ (use your locally installed `mvn`; no wrapper is shipped)

## Build

```sh
mvn -B verify          # full build, runs tests and Spotless format check
mvn spotless:apply     # auto-fix Java formatting (Google Java Format)
```

`spotless:check` runs as part of `verify`, so unformatted code fails the build
locally and in CI. Run `spotless:apply` before committing.

## Module map

| Directory | Artifact | Role |
| --- | --- | --- |
| [`netty-codec-webtransport/`](netty-codec-webtransport/) | `netty-codec-webtransport` | The codec — frames, capsules, handlers, server, client, session API. Mirrors `netty-codec-http3`. |
| [`netty-codec-webtransport-example/`](netty-codec-webtransport-example/) | `netty-codec-webtransport-example` | Runnable demos. Empty until the codec has a usable session API. |

## Documentation

- [docs/architecture.md](docs/architecture.md) — design rules: zero-copy data
  path, threading model, reference-counting discipline, Java 21 idioms.
- [docs/netty-stack.md](docs/netty-stack.md) — Netty 4.2 dependency map, native
  classifiers, JDK requirements, gaps the codec must fill.
- [docs/specs.md](docs/specs.md) — curated reading guide into the vendored specs.
- [docs/project-layout.md](docs/project-layout.md) — module / package conventions
  and Maven layout rationale.
- [docs/roadmap.md](docs/roadmap.md) — phased implementation plan.
- [docs/wire-format.md](docs/wire-format.md) — reserved; will map codec classes
  to spec sections once the implementation lands.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE.txt](NOTICE.txt).
The license is asserted at the repository root only; individual `.java` files
do not carry per-file license headers.

## Security

Security reports go to the address in [SECURITY.md](SECURITY.md), not to public
issue trackers.
