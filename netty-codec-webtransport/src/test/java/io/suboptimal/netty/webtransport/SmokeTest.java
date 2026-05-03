package io.suboptimal.netty.webtransport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SmokeTest {

  @Test
  void buildsAndLoads() {
    assertThat(Math.addExact(1, 1)).isEqualTo(2);
  }
}
