# netty-codec-webtransport-example

Runnable WebTransport demos.

## EchoServer — manual browser test harness

A self-contained WebTransport echo server with a small built-in HTML page.
Run it, point Chrome at the printed URL, click "Connect" — the page opens a
WebTransport session and the buttons exercise bidi streams, uni streams, and
datagrams.

### Run

From the repository root:

```sh
mvn -B compile                                                  # one-time
mvn -B -pl netty-codec-webtransport-example exec:java
```

You should see a banner like:

```
==============================================================
 WebTransport echo server is running.

 1. Open this URL in Chrome (or any Chromium-based browser):
        http://localhost:8080/
 2. Click "Connect" — the page reads /cert.json automatically and
    establishes a WebTransport session over UDP 4433.
 3. Use the buttons to send bidi streams, uni streams, and datagrams.

 WT endpoint:    https://localhost:4433/echo
 Cert (sha-256): oJ2y5gkRtcq6vHhadvkKBFkrqLBkmAOJFLczLCpsDtQ=  (base64)
                 a09db2e60911b5cababc785a76f90a04592ba8b06498038914b7332c2a6c0ed4  (hex)

 Override ports with WT_PORT and HTTP_PORT env vars.
 Press Ctrl-C to stop.
==============================================================
```

Open `http://localhost:8080/` and click around. The page UI:

- **Connect** — fetches `/cert.json`, builds a `Uint8Array` of the cert hash,
  calls `new WebTransport(url, { serverCertificateHashes: [{...}] })`, awaits
  `ready`. No Chrome flags or system trust changes are needed.
- **Send bidi stream + read echo** — opens a bidi stream, writes the input,
  reads the server's mirror back.
- **Send uni stream + await server uni reply** — writes one uni stream; the
  server reads it and opens a fresh server-initiated uni stream with the same
  payload, which the page reads.
- **Send datagram + read echo** — writes a datagram; a background reader
  logs every inbound datagram, including the server's mirror.
- **Read next server-initiated bidi stream** — blocks on
  `incomingBidirectionalStreams` until the server opens one. Useful for
  confirming server-push works (currently the server doesn't initiate streams
  on its own — wire a trigger in `EchoSessionHandler` if you want one).
- **Close session** — sends `wt.close({ closeCode: 7, reason: "manual close" })`
  and the page logs `wt.closed`'s resolution.

### Why the served-page-on-:8080 dance?

WebTransport in Chrome requires a **secure context**. `file://` URLs are not
considered secure for this purpose; a real `https://` page would work but
adds another self-signed cert to trust. `http://localhost:*` *is* a secure
context per Chrome's rules, so we serve the test page there with the JDK
built-in `com.sun.net.httpserver.HttpServer` — no extra deps, no extra cert.

### Why a small ECDSA cert with ≤14-day validity?

That's the constraint the WebTransport spec imposes on the
`serverCertificateHashes` JS option. Chromium enforces it strictly:

- key algorithm: ECDSA, curve P-256
- validity: `notAfter - notBefore` ≤ 14 days

`SelfSignedCertificate("localhost", notBefore, notAfter, "EC", 256)` with a
13-day window meets both.

## Planned demos

- **FileTransfer** — server-side handler that streams a file over a uni
  stream; demonstrates backpressure and the zero-copy data path.
- **DatagramChat** — broadcast datagrams between connected sessions;
  exercises the datagram fast path and the session registry.
