# docs

Index of design and reference docs for `netty-codec-webtransport`.

| File | Purpose |
| --- | --- |
| [architecture.md](architecture.md) | Design rules: zero-copy data path, threading model, reference-counting discipline, Java 21 idioms. **Read before proposing handler or buffer-handling changes.** |
| [netty-stack.md](netty-stack.md) | Netty 4.2 dependency map, QUIC native classifiers, JDK requirements, the gaps WebTransport must fill on top of `netty-codec-http3`. |
| [specs.md](specs.md) | Reading guide into the vendored IETF / W3C specs in [`../specs/`](../specs/), with an inline glossary. |
| [project-layout.md](project-layout.md) | Module / package conventions, Maven layout rationale, formatting and licensing policy. |
| [roadmap.md](roadmap.md) | Phased implementation plan. Server-first; the priority client is the browser via the W3C WebTransport JS API. |
| [wire-format.md](wire-format.md) | Spec-to-class map: which codec class implements which section of which spec. |

For non-doc context:

- [`../README.md`](../README.md) — project pitch, status, build commands.
- [`../CLAUDE.md`](../CLAUDE.md) — short pointer doc for AI / human sessions.
- [`../specs/README.md`](../specs/README.md) — provenance for vendored specs.
