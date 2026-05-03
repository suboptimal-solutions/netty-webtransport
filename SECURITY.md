# Security Policy

WebTransport is a transport-layer protocol carrying potentially untrusted data over
TLS 1.3 + QUIC. Bugs here can affect the confidentiality, integrity, or availability
of an entire HTTP/3 connection. Please report security issues responsibly.

## Reporting a vulnerability

**Do not open a public GitHub issue for a suspected vulnerability.**

Email the maintainers privately at:

  security@suboptimal.io

Include:

- A description of the vulnerability (what, where, how exploited)
- Impact assessment (DoS, info leak, RCE, ...)
- Reproduction steps or a proof-of-concept (a failing test case is ideal)
- Affected version(s) of `netty-codec-webtransport`
- Your contact for follow-up

We will acknowledge receipt within 5 business days and aim to provide a remediation
plan within 30 days. Public disclosure is targeted for **90 days** after the initial
report, or sooner if a fix is published, whichever comes first. Coordinated disclosure
windows can be extended for severe issues by mutual agreement.

## Scope

In scope:

- Memory safety, reference-counting bugs in `io.suboptimal.netty.webtransport.*`
- Wire-format parsing bugs that crash, panic, or misbehave on adversarial input
- Authentication / origin-handling bypasses
- Datagram amplification, resource-exhaustion, or unbounded-allocation paths
- TLS / ALPN handshake handling specific to this codec

Out of scope:

- Vulnerabilities in upstream Netty (report to https://netty.io)
- Vulnerabilities in Cloudflare quiche (report to https://github.com/cloudflare/quiche)
- Vulnerabilities in BoringSSL (report via Google's process)
- Theoretical issues in the WebTransport drafts themselves (raise on the IETF mailing list)

## Supported versions

This project is pre-1.0. Only the latest released version is supported for security
fixes. Once the project ships 1.0, this policy will be updated to specify a longer
support window.
