package io.suboptimal.netty.webtransport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebTransportProtocolTest {

    @Test
    void errorCodeRoundTrip() {
        for (long n = 0; n < 256; n++) {
            long h = WebTransportProtocol.webtransportCodeToHttpCode(n);
            assertThat(WebTransportProtocol.isWebtransportApplicationError(h)).isTrue();
            assertThat(WebTransportProtocol.httpCodeToWebtransportCode(h)).isEqualTo(n);
        }
    }

    @Test
    void errorCodeBoundaries() {
        long first = WebTransportProtocol.webtransportCodeToHttpCode(0);
        assertThat(first).isEqualTo(WebTransportProtocol.WT_APPLICATION_ERROR_FIRST);

        long last = WebTransportProtocol.webtransportCodeToHttpCode(0xFFFFFFFFL);
        assertThat(last).isEqualTo(WebTransportProtocol.WT_APPLICATION_ERROR_LAST);
    }

    @Test
    void reservedCodepointsAreSkipped() {
        for (long n = 0; n < 128; n++) {
            long h = WebTransportProtocol.webtransportCodeToHttpCode(n);
            assertThat((h - 0x21L) % 0x1fL).isNotZero();
        }
    }
}
