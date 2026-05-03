# Vendored specifications

This directory holds offline copies of the WebTransport specification family and
adjacent RFCs. They are vendored so the codebase has a stable, greppable, version-pinned
reference even as the upstream drafts evolve.

**Do not edit these files.** They are byte-exact copies of the upstream documents.
The repository's `.gitattributes` marks them as binary so Windows checkouts will
not rewrite line endings, which would invalidate the sha256 sums below.

## Provenance

| File | Canonical URL | Identifier | Fetched (UTC) | sha256 |
| --- | --- | --- | --- | --- |
| [`draft-ietf-webtrans-overview-12.txt`](draft-ietf-webtrans-overview-12.txt) | `https://www.ietf.org/archive/id/draft-ietf-webtrans-overview-12.txt` | `draft-ietf-webtrans-overview-12` | 2026-05-03T16:58:00Z | `7fd76162b85536280d4905e10349ac1daed343c974f952b0942ec24083d4edd2` |
| [`draft-ietf-webtrans-http3-15.txt`](draft-ietf-webtrans-http3-15.txt) | `https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-15.txt` | `draft-ietf-webtrans-http3-15` | 2026-05-03T16:58:00Z | `1d35bf487797c47bd54bda64e10786953b37287c424d23f3cd939ddb04ad4383` |
| [`draft-ietf-webtrans-http2-14.txt`](draft-ietf-webtrans-http2-14.txt) | `https://www.ietf.org/archive/id/draft-ietf-webtrans-http2-14.txt` | `draft-ietf-webtrans-http2-14` | 2026-05-03T16:58:00Z | `3338a16d1c56c628eca659d31648c062055e01d28aecb76358851b0efe211025` |
| [`rfc9220.txt`](rfc9220.txt) | `https://www.rfc-editor.org/rfc/rfc9220.txt` | RFC 9220 — Bootstrapping WebSockets with HTTP/3 | 2026-05-03T16:58:00Z | `4bac5e1368db199468345dbd2d31ebb4abb7509732b1ae08108cc4860e8967db` |
| [`rfc9221.txt`](rfc9221.txt) | `https://www.rfc-editor.org/rfc/rfc9221.txt` | RFC 9221 — An Unreliable Datagram Extension to QUIC | 2026-05-03T16:58:00Z | `ee8c04c5228fd120030ba7a8f6725c2ca609da107ad2ba8c44fdd44f73edb3b4` |
| [`rfc9297.txt`](rfc9297.txt) | `https://www.rfc-editor.org/rfc/rfc9297.txt` | RFC 9297 — HTTP Datagrams and the Capsule Protocol | 2026-05-03T16:58:00Z | `c60346e6e48a63c8ca80b361467c81c9e764e1bb9f2c29535697ac255bd037b5` |
| [`w3c-webtransport.html`](w3c-webtransport.html) | `https://www.w3.org/TR/webtransport/` | W3C WebTransport (Working Draft, single-page export) | 2026-05-03T16:58:00Z | `2ef92d288c0d362bef824f4ea9380bbe26e6a794536c8c55802bfbfa8ed805cc` |

## Refreshing a spec

When a new draft revision lands, replace both the file and its row in the table
**in the same commit**. Reproducible refresh:

```sh
# 1. Find the latest draft revision for a given doc.
curl -s https://datatracker.ietf.org/doc/draft-ietf-webtrans-http3/ \
  | grep -oE 'draft-ietf-webtrans-http3-[0-9]+' | sort -u | tail -1

# 2. Fetch the .txt at that revision.
curl -sSfL https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-NN.txt \
  -o specs/draft-ietf-webtrans-http3-NN.txt

# 3. Recompute the sha256 and update this README.
shasum -a 256 specs/draft-ietf-webtrans-http3-NN.txt
date -u +"%Y-%m-%dT%H:%M:%SZ"
```

For RFCs the canonical URL is `https://www.rfc-editor.org/rfc/rfcNNNN.txt`; they
do not change once published, so a refresh is only needed if you want to verify
the sha256 against the authoritative source.

## Why we vendor

- **Implementation reference.** Future codec classes cite spec sections by file
  path and section number (e.g. `specs/draft-ietf-webtrans-http3-15.txt §4.2`),
  not by URL. URLs link-rot.
- **Reproducible review.** A reviewer can `grep` the exact paragraph the code
  was written against, even if upstream has moved on.
- **Frozen targets.** Drafts change between revisions. Pinning the bytes makes
  "we implement what the spec said on 2026-05-03" verifiable.

## Related upstream resources (not vendored)

- IETF WEBTRANS working group: https://datatracker.ietf.org/wg/webtrans/
- WebTransport draft GitHub repos: https://github.com/ietf-wg-webtrans
- W3C WebTransport spec source: https://github.com/w3c/webtransport
