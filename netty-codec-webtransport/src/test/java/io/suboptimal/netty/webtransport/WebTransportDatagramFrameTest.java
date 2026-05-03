package io.suboptimal.netty.webtransport;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class WebTransportDatagramFrameTest {

    @Test
    void retainAndReleaseFollowContent() {
        ByteBuf payload = Unpooled.wrappedBuffer(new byte[] {1, 2, 3});
        WebTransportDatagramFrame frame = new WebTransportDatagramFrame(payload);

        assertThat(frame.refCnt()).isEqualTo(1);
        frame.retain();
        assertThat(frame.refCnt()).isEqualTo(2);
        frame.release();
        assertThat(frame.refCnt()).isEqualTo(1);
        assertThat(frame.release()).isTrue();
        assertThat(payload.refCnt()).isZero();
    }

    @Test
    void duplicateSharesStorageAndRefcount() {
        ByteBuf payload = Unpooled.wrappedBuffer(new byte[] {7, 8, 9});
        WebTransportDatagramFrame frame = new WebTransportDatagramFrame(payload);
        WebTransportDatagramFrame dup = frame.duplicate();

        assertThat(dup.content().readableBytes()).isEqualTo(3);
        // duplicate() shares both storage and refcount; releasing dup also releases the original.
        assertThat(dup.release()).isTrue();
        assertThat(payload.refCnt()).isZero();
    }

    @Test
    void retainedDuplicateIncrementsRefcount() {
        ByteBuf payload = Unpooled.wrappedBuffer(new byte[] {0, 1});
        WebTransportDatagramFrame frame = new WebTransportDatagramFrame(payload);
        WebTransportDatagramFrame dup = frame.retainedDuplicate();

        assertThat(payload.refCnt()).isEqualTo(2);
        dup.release();
        assertThat(payload.refCnt()).isEqualTo(1);
        frame.release();
    }

    @Test
    void copyDoesNotShareStorage() {
        ByteBuf payload = Unpooled.wrappedBuffer(new byte[] {4, 5, 6});
        WebTransportDatagramFrame frame = new WebTransportDatagramFrame(payload);
        WebTransportDatagramFrame copy = frame.copy();

        assertThat(copy.content()).isNotSameAs(payload);
        assertThat(copy.content().readableBytes()).isEqualTo(3);
        copy.release();
        assertThat(payload.refCnt()).isEqualTo(1);
        frame.release();
    }
}
