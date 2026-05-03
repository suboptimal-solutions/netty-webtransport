# netty-codec-webtransport-example

Runnable WebTransport demos. **Empty at the moment** — this module exists so future
PRs can add demo classes (`EchoServer`, `EchoClient`, ...) without restructuring the
build.

## Planned demos

These land alongside roadmap phase 7 (see [`docs/roadmap.md`](../docs/roadmap.md)):

- **EchoServer** — accepts a WebTransport session, echoes every bidirectional and
  unidirectional stream payload, mirrors every datagram. Used as the conformance
  target for browser interop.
- **FileTransfer** — server-side handler that streams a file over a unidirectional
  stream; demonstrates backpressure and the zero-copy data path.
- **DatagramChat** — broadcast datagrams between connected sessions; exercises
  the datagram fast path and the session registry.

Until those exist, `mvn` builds this module as an empty JAR (`<skipTests>true</skipTests>`).
