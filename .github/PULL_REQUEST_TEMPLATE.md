## Summary

<!-- One paragraph: what does this change, and why? -->

## Spec link

<!-- For protocol changes, cite the section, e.g.
     `specs/draft-ietf-webtrans-http3-15.txt §4.2`.
     For pure refactors, say "n/a (refactor)". -->

## Checklist

- [ ] Tests added or updated under `netty-codec-webtransport/src/test/java`
- [ ] `mvn spotless:apply` run; `mvn -B verify` passes locally
- [ ] No `byte[]` allocation, no `String.getBytes(...)`, no
      `ByteBuf.toString(Charset)`, no `ByteBuf.getBytes(...)` on hot paths
      (see [docs/architecture.md §2](../docs/architecture.md#2-zero-copy-data-path))
- [ ] Reference-counting discipline observed
      (see [docs/architecture.md §4](../docs/architecture.md#4-reference-counting-discipline))
- [ ] If touching the wire format, [`docs/wire-format.md`](../docs/wire-format.md)
      updated accordingly

## Notes for reviewers

<!-- Anything subtle: handler ordering, ByteBuf ownership across boundaries,
     spec ambiguity you had to resolve, etc. -->
