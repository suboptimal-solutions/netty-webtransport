# Wire format → spec map

**Reserved.** Once codec classes exist in
`io.suboptimal.netty.webtransport`, this doc maps each public class to the
spec section it implements, e.g.

> `WtCapsuleDecoder` → [`specs/draft-ietf-webtrans-http3-15.txt`](../specs/draft-ietf-webtrans-http3-15.txt)
> §6 + [`specs/rfc9297.txt`](../specs/rfc9297.txt) §3.

The intent is one source of truth a reviewer can follow when checking whether
the implementation matches the spec it claims to. Until codec classes exist,
[`specs.md`](specs.md) is the entrypoint.
