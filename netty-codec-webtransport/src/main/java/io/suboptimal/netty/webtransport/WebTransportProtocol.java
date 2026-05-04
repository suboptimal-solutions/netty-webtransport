package io.suboptimal.netty.webtransport;

import io.netty.util.AsciiString;

/**
 * Wire-format constants for WebTransport over HTTP/3.
 *
 * <p>draft-ietf-webtrans-http3-15 §9, RFC 9297 §5.
 */
public final class WebTransportProtocol {

    private WebTransportProtocol() {}

    // --- Upgrade token (§9.1) ---

    public static final AsciiString UPGRADE_TOKEN = AsciiString.cached("webtransport-h3");

    // Legacy upgrade token used by drafts -02 through -07. Current Chromium / QUICHE
    // (as of Chrome 147) ships these drafts only, so we accept either token on the
    // server side until browsers catch up to draft-15. See §7.1 ("Negotiating the
    // Draft Version") for the multi-version interop story.
    public static final AsciiString UPGRADE_TOKEN_DRAFT07 = AsciiString.cached("webtransport");

    // --- HTTP/3 SETTINGS (§9.2) ---

    public static final long SETTINGS_WT_ENABLED = 0x2c7cf000L;
    public static final long SETTINGS_ENABLE_CONNECT_PROTOCOL = 0x08L;
    public static final long SETTINGS_H3_DATAGRAM = 0x33L;
    public static final long SETTINGS_WT_INITIAL_MAX_STREAMS_UNI = 0x2b64L;
    public static final long SETTINGS_WT_INITIAL_MAX_STREAMS_BIDI = 0x2b65L;
    public static final long SETTINGS_WT_INITIAL_MAX_DATA = 0x2b61L;

    // Draft-02 SETTINGS_ENABLE_WEBTRANSPORT — required for Chromium's default code path:
    // QUICHE supports both draft-02 and draft-07, but Chrome's
    // DedicatedWebTransportHttp3ClientSession gates draft-07 behind the
    // kEnableWebTransportDraft07 feature flag, so without this flag the only version
    // Chrome will negotiate is draft-02. Sending all three codepoints lets a single
    // server speak to draft-02 / draft-07 / draft-15 peers without configuration.
    public static final long SETTINGS_WT_ENABLED_DRAFT02 = 0x2b603742L;

    // Draft-07 SETTINGS_WEBTRANSPORT_MAX_SESSIONS — value is the max-concurrent-sessions
    // count rather than draft-15's boolean. Sent alongside SETTINGS_WT_ENABLED so a
    // QUICHE-based peer (Chrome) can negotiate WebTransport via its draft-07 codepath.
    public static final long SETTINGS_WT_MAX_SESSIONS_DRAFT07 = 0xc671706aL;

    // --- Frame type (§9.3) ---

    public static final long WT_STREAM_FRAME_TYPE = 0x41L;

    // --- Stream type (§9.4) ---

    public static final long WT_UNI_STREAM_TYPE = 0x54L;

    // --- Error codes (§9.5) ---

    public static final long WT_BUFFERED_STREAM_REJECTED = 0x3994bd84L;
    public static final long WT_SESSION_GONE = 0x170d7b68L;
    public static final long WT_FLOW_CONTROL_ERROR = 0x045d4487L;
    public static final long WT_ALPN_ERROR = 0x0817b3ddL;
    public static final long WT_REQUIREMENTS_NOT_MET = 0x212c0d48L;

    public static final long WT_APPLICATION_ERROR_FIRST = 0x52e4a40fa8dbL;
    public static final long WT_APPLICATION_ERROR_LAST = 0x52e5ac983162L;

    // --- Capsule types (§9.6) ---

    public static final long CAPSULE_CLOSE_SESSION = 0x2843L;
    public static final long CAPSULE_DRAIN_SESSION = 0x78aeL;
    public static final long CAPSULE_MAX_STREAMS_BIDI = 0x190B4D3FL;
    public static final long CAPSULE_MAX_STREAMS_UNI = 0x190B4D40L;
    public static final long CAPSULE_MAX_DATA = 0x190B4D3DL;
    public static final long CAPSULE_DATA_BLOCKED = 0x190B4D41L;
    public static final long CAPSULE_STREAMS_BLOCKED_BIDI = 0x190B4D43L;
    public static final long CAPSULE_STREAMS_BLOCKED_UNI = 0x190B4D44L;

    // --- HTTP pseudo-headers ---

    public static final AsciiString METHOD_CONNECT = AsciiString.cached("CONNECT");
    public static final AsciiString SCHEME_HTTPS = AsciiString.cached("https");

    // --- Application error code mapping (§4.4) ---

    public static long webtransportCodeToHttpCode(long n) {
        return WT_APPLICATION_ERROR_FIRST + n + n / 0x1eL;
    }

    public static long httpCodeToWebtransportCode(long h) {
        long shifted = h - WT_APPLICATION_ERROR_FIRST;
        return shifted - shifted / 0x1fL;
    }

    public static boolean isWebtransportApplicationError(long h) {
        return h >= WT_APPLICATION_ERROR_FIRST
                && h <= WT_APPLICATION_ERROR_LAST
                && (h - 0x21L) % 0x1fL != 0;
    }
}
