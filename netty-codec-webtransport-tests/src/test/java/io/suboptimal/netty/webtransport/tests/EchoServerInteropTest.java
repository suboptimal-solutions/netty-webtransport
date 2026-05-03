package io.suboptimal.netty.webtransport.tests;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests that drive Chromium through Playwright against {@link InteropEchoServer},
 * exercising real WebTransport-over-HTTP/3 wire compatibility.
 *
 * <p>Run with:
 *
 * <pre>
 *   mvn -B verify -P integration                                  # headless
 *   mvn -B verify -P integration -Dpw.headed=true                 # visible browser; pauses on failure
 *   mvn -B verify -P integration -Dpw.headed=true -Dpw.devtools=true   # + DevTools open
 *   mvn -B verify -P integration -Dpw.headed=true -Dpw.slowmo=500      # slow each action 500 ms
 *   mvn -B verify -P integration -Dpw.headed=true -Dpw.holdOpen=30     # hold 30 min on failure
 * </pre>
 *
 * <p>When {@code pw.headed=true} and a connection fails, the harness opens
 * {@code chrome://webtransport-internals} and {@code chrome://net-internals/#quic} as extra tabs
 * and pauses the test for {@code pw.holdOpen} minutes (default 10) so the failure can be
 * inspected. Kill the build (Ctrl-C) when done.
 */
class EchoServerInteropTest {

    private static InteropEchoServer server;
    private static Playwright playwright;
    private static Browser browser;
    private static String htmlContent;
    private static String pageUrl;

    @BeforeAll
    static void setupAll() throws Exception {
        server = InteropEchoServer.start();

        // Cert trust is supplied via serverCertificateHashes (the W3C-spec mechanism), so we
        // need no cert-bypass Chromium flags. We connect to 127.0.0.1 (IPv4) to avoid macOS's
        // localhost-resolves-to-::1 behaviour, since the in-process server binds to 127.0.0.1.
        List<String> args = new ArrayList<>();

        boolean headed = Boolean.parseBoolean(System.getProperty("pw.headed", "false"));
        boolean devtools = Boolean.parseBoolean(System.getProperty("pw.devtools", "false"));
        double slowMo = Double.parseDouble(System.getProperty("pw.slowmo", "0"));

        if (devtools) {
            // Playwright dropped LaunchOptions.setDevtools in 1.46; we pass the Chromium flag
            // directly. `--auto-open-devtools-for-tabs` opens DevTools for every new tab.
            args.add("--auto-open-devtools-for-tabs");
            // Forces headed mode even if pw.headed wasn't set; DevTools needs a window.
            headed = true;
        }

        BrowserType.LaunchOptions launchOptions =
                new BrowserType.LaunchOptions()
                        .setArgs(args)
                        .setHeadless(!headed)
                        .setSlowMo(slowMo);

        playwright = Playwright.create();
        browser = playwright.chromium().launch(launchOptions);

        try (InputStream in =
                EchoServerInteropTest.class.getResourceAsStream(
                        "/io/suboptimal/netty/webtransport/tests/echo.html")) {
            if (in == null) throw new IllegalStateException("echo.html not on test classpath");
            htmlContent = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // We serve the page from a fake HTTPS URL via Playwright's route interception. Real
        // HTTPS gives us a secure context; WebTransport's serverCertificateHashes requires that
        // (file:// is not considered secure for WebTransport in Chromium).
        pageUrl = "https://test.localhost/echo.html";
    }

    @AfterAll
    static void teardownAll() throws Exception {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
        if (server != null) server.close();
    }

    private BrowserContext context;
    private Page page;

    @BeforeEach
    void openContext() {
        server.observations().clear();
        context = browser.newContext();
        // Intercept any request to test.localhost and return our fixture HTML, giving us an
        // https origin without an actual HTTPS server.
        context.route(
                "https://test.localhost/**",
                route ->
                        route.fulfill(
                                new com.microsoft.playwright.Route.FulfillOptions()
                                        .setStatus(200)
                                        .setContentType("text/html; charset=utf-8")
                                        .setBody(htmlContent)));
        page = context.newPage();
        page.onConsoleMessage(
                msg -> System.err.println("[browser." + msg.type() + "] " + msg.text()));
        page.onPageError(err -> System.err.println("[browser.error] " + err));
        page.navigate(pageUrl);

        // Convert the server's cert hash to a JS-friendly List<Integer> (Playwright Java's
        // serializer accepts boxed Integer lists but not primitive int[]; the page rebuilds it
        // as a Uint8Array).
        byte[] hashBytes = server.certSha256();
        List<Integer> hashInts = new ArrayList<>(hashBytes.length);
        for (byte b : hashBytes) {
            hashInts.add(b & 0xFF);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> result =
                (Map<String, Object>)
                        page.evaluate(
                                "(args) => window.connect(args)",
                                Map.of(
                                        "url", "https://127.0.0.1:" + server.port() + "/echo",
                                        "certHash", hashInts));
        if (!Boolean.TRUE.equals(result.get("ok"))) {
            holdOpenIfHeaded("WebTransport connect failed: " + result);
            throw new IllegalStateException("WebTransport connect failed: " + result);
        }
    }

    /**
     * When running with {@code -Dpw.headed=true}, pause the test so the browser stays open for
     * manual inspection. Auto-opens {@code chrome://net-internals/#quic} and
     * {@code chrome://net-internals/#events} as extra tabs — the {@code #events} view has every
     * networking event including the actual WT rejection reason; the {@code #quic} view shows
     * live QUIC sessions. The DevTools Network panel on the test page itself is also worth
     * checking (use {@code -Dpw.devtools=true} to open it automatically).
     *
     * <p>Hold duration: {@code -Dpw.holdOpen} minutes (default 10). Set to 0 to disable.
     *
     * <p>Headless runs are unaffected — they just throw and let surefire move on.
     */
    private void holdOpenIfHeaded(String reason) {
        if (!Boolean.parseBoolean(System.getProperty("pw.headed", "false"))) {
            return;
        }
        long minutes = Long.parseLong(System.getProperty("pw.holdOpen", "10"));
        if (minutes <= 0) {
            return;
        }
        try {
            context.newPage().navigate("chrome://net-internals/#events");
        } catch (Exception ignored) {
        }
        try {
            context.newPage().navigate("chrome://net-internals/#quic");
        } catch (Exception ignored) {
        }
        System.out.println("==============================================================");
        System.out.println("INTEROP TEST PAUSED for manual inspection (" + minutes + " min).");
        System.out.println("Reason: " + reason);
        System.out.println("Inspect tabs:");
        System.out.println("  - chrome://net-internals/#events  (filter by URL for the actual error)");
        System.out.println("  - chrome://net-internals/#quic    (live QUIC sessions and errors)");
        System.out.println("  - DevTools → Network panel on the test page");
        System.out.println("Kill the build (Ctrl-C) when done, or wait for the timeout.");
        System.out.println("Override timeout with -Dpw.holdOpen=<minutes> (0 disables).");
        System.out.println("==============================================================");
        try {
            Thread.sleep(TimeUnit.MINUTES.toMillis(minutes));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void closeContext() {
        if (context != null) context.close();
    }

    // ─── Tests ──────────────────────────────────────────────────────────

    @Test
    void bidiEchoRoundTrip() throws Exception {
        long sessionId = waitForEstablished();

        String result = (String) page.evaluate("(t) => window.echoBidi(t)", "hello bidi");
        assertThat(result).isEqualTo("hello bidi");

        Observation streamOpened = pollFor(Observation.BidiStreamOpened.class);
        assertThat(((Observation.BidiStreamOpened) streamOpened).sessionId()).isEqualTo(sessionId);

        Observation payload = pollFor(Observation.StreamPayload.class);
        assertThat(((Observation.StreamPayload) payload).text()).isEqualTo("hello bidi");
    }

    @Test
    void uniStreamRoundTrip() throws Exception {
        long sessionId = waitForEstablished();

        String result = (String) page.evaluate("(t) => window.echoUni(t)", "hello uni");
        assertThat(result).isEqualTo("hello uni");

        Observation streamOpened = pollFor(Observation.UniStreamOpened.class);
        assertThat(((Observation.UniStreamOpened) streamOpened).sessionId()).isEqualTo(sessionId);

        Observation payload = pollFor(Observation.StreamPayload.class);
        assertThat(((Observation.StreamPayload) payload).text()).isEqualTo("hello uni");
    }

    @Test
    void datagramRoundTrip() throws Exception {
        long sessionId = waitForEstablished();

        String result = (String) page.evaluate("(t) => window.echoDatagram(t)", "hello dgram");
        assertThat(result).isEqualTo("hello dgram");

        Observation dg = pollFor(Observation.DatagramReceived.class);
        Observation.DatagramReceived rec = (Observation.DatagramReceived) dg;
        assertThat(rec.sessionId()).isEqualTo(sessionId);
        assertThat(rec.text()).isEqualTo("hello dgram");
    }

    @Test
    void serverInitiatedBidiStream() throws Exception {
        long sessionId = waitForEstablished();

        // Start the JS observer; it leaves a promise pending that we await later.
        page.evaluate("() => { window.__pendingIncomingBidi = window.observeIncomingBidi(); }");

        server.openBidiStream(sessionId, "server-hello").await(5, TimeUnit.SECONDS);

        String received = (String) page.evaluate("() => window.__pendingIncomingBidi");
        assertThat(received).isEqualTo("server-hello");
    }

    @Test
    void cleanSessionClose() throws Exception {
        long sessionId = waitForEstablished();

        @SuppressWarnings("unchecked")
        Map<String, Object> result =
                (Map<String, Object>)
                        page.evaluate(
                                "(args) => window.gracefulClose(args.code, args.reason)",
                                Map.of("code", 7, "reason", "bye"));

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(((Number) result.get("closeCode")).intValue()).isEqualTo(7);
        assertThat(result.get("reason")).isEqualTo("bye");

        Observation closed = pollFor(Observation.SessionClosed.class);
        Observation.SessionClosed sc = (Observation.SessionClosed) closed;
        assertThat(sc.sessionId()).isEqualTo(sessionId);
        assertThat(sc.errorCode()).isEqualTo(7);
        assertThat(sc.errorMessage()).isEqualTo("bye");
    }

    @Test
    void drainSignal() throws Exception {
        long sessionId = waitForEstablished();

        server.drainSession(sessionId);

        // wt.draining is a Promise; if DRAIN already arrived, this resolves immediately.
        page.evaluate("() => window.observeDraining()");
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private long waitForEstablished() throws InterruptedException {
        Observation o = pollFor(Observation.SessionEstablished.class);
        return ((Observation.SessionEstablished) o).sessionId();
    }

    private Observation pollFor(Class<? extends Observation> type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Observation o =
                    server.observations()
                            .poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (o == null) break;
            if (type.isInstance(o)) return o;
            // Not what we were waiting for — keep going.
        }
        throw new AssertionError("Timed out waiting for observation of type " + type.getSimpleName());
    }
}
