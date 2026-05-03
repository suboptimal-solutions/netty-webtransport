# CLAUDE.md

Guidance for AI / human sessions working on this repository. Read this first.

## What this project is

`netty-codec-webtransport` — a Java library implementing WebTransport over HTTP/3
on top of Netty 4.2.x. Pre-1.0; no protocol code yet. The bootstrap establishes
the skeleton, vendors the IETF / W3C specs, and writes the design rules future
implementers must follow.

## Spec target (May 2026)

Implementing `draft-ietf-webtrans-http3-15` plus RFC 9220 / 9221 / 9297. Exact
bytes, URLs, and sha256 sums are pinned in [specs/README.md](specs/README.md).
**When the spec changes, update both the vendored file and `specs/README.md` in
the same commit.**

## Module map

- [`netty-codec-webtransport/`](netty-codec-webtransport/) — the codec. Frames,
  capsules, handlers (server + client), session API. Tests in `src/test/java`.
- [`netty-codec-webtransport-example/`](netty-codec-webtransport-example/) —
  runnable demos. Empty until the codec has a usable session API.

## Where things live

- Frame / capsule parsers, varint helpers → `io.suboptimal.netty.webtransport.*`
  in the codec module.
- Server handler, client handler, session API → same package.
- Internal-only classes → `io.suboptimal.netty.webtransport.internal.*` (not
  introduced until a class genuinely is internal).
- Demo `EchoServer` / `EchoClient` → `io.suboptimal.netty.webtransport.example`
  in the example module (lands in roadmap phase 7).
- Vendored specs → [`specs/`](specs/), one file per spec. Read with `less`,
  not a browser; they are the source of truth.
- Design notes → [`docs/architecture.md`](docs/architecture.md). Read before
  proposing any new handler or buffer-handling change.

## Three hard rules

1. **Don't copy bytes — slice them.** `ByteBuf.slice()` /
   `retainedSlice()` / `readSlice(int)` over `getBytes(...)`,
   `toString()`, or any path that allocates a `byte[]`. Details in
   [docs/architecture.md#zero-copy-data-path](docs/architecture.md).
2. **Don't block the event loop.** Codec / handler code runs on a Netty event
   loop. Virtual threads are forbidden there; allowed only in application code
   that consumes the session API.
3. **Cite the spec section.** Every codec class and non-trivial handler change
   should reference the exact section of the relevant `specs/*.txt` (e.g.
   `draft-ietf-webtrans-http3-15 §4.2`). Code reviews check this.

## Build and format

```sh
mvn spotless:apply     # auto-fix formatting (Google Java Format)
mvn -B verify          # full build, runs tests and Spotless check
```

`mvn verify` includes `spotless:check`. CI fails on unformatted code. There is
no Maven wrapper — use your locally installed `mvn` (3.9+).

## What's next

See [docs/roadmap.md](docs/roadmap.md). The first protocol task is varint and
frame primitives in the codec module. Server-first; the priority client is the
browser via the W3C WebTransport JS API. A Java client lands late.

## What NOT to do

- Don't add per-file Apache 2.0 headers — licensing is at the repo root only.
- Don't depend on `io.netty.incubator:netty-incubator-codec-quic` or
  `netty-incubator-codec-http3`; those are superseded by graduated artifacts in
  `io.netty` core since Netty 4.2.1.Final.
- Don't introduce a Maven wrapper.
- Don't write documentation files Claude wasn't asked for.
