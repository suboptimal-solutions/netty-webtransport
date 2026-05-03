---
name: Bug report
about: Report unexpected behavior in netty-codec-webtransport
title: "[bug] "
labels: bug
---

## What spec section is involved?

Cite the relevant section of a vendored spec, e.g.
`specs/draft-ietf-webtrans-http3-15.txt §4.1` or `specs/rfc9297.txt §3.1`.
"None / not sure" is a valid answer; please say so explicitly.

## What did you expect?

Quote the spec text or describe the expected behavior.

## What happened instead?

Stack trace, hex dump of the offending wire bytes, log excerpt — whatever's
reproducible.

## Reproduction

Minimum code or command sequence. A failing JUnit 5 test under
`netty-codec-webtransport/src/test/java` is ideal.

## Environment

- `netty-codec-webtransport` version:
- Netty version (from `mvn dependency:tree`):
- JDK (`java -version`):
- OS / architecture:
- Browser (for interop reports):
