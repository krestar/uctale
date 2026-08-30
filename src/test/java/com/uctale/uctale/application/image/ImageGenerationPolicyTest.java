package com.uctale.uctale.application.image;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageGenerationPolicyTest {

    @Test
    @DisplayName("asset 발급 시 model/size/safe/style과 seed를 한 번 확정한다")
    void issue_FreezesGenerationSpec() {
        SecureRandom random = new SecureRandom() {
            @Override
            public int nextInt(int bound) {
                return 123456;
            }
        };
        ImageGenerationPolicy policy = new ImageGenerationPolicy(
                "flux", 1024, 576, 768, true, "uctale-charcoal-v1", random
        );

        ImageGenerationPolicy.GenerationSpec landscape = policy.issue("16:9");
        ImageGenerationPolicy.GenerationSpec square = policy.issue("1:1");

        assertThat(landscape.model()).isEqualTo("flux");
        assertThat(landscape.width()).isEqualTo(1024);
        assertThat(landscape.height()).isEqualTo(576);
        assertThat(landscape.seed()).isEqualTo(123456);
        assertThat(landscape.safe()).isTrue();
        assertThat(landscape.styleVersion()).isEqualTo("uctale-charcoal-v1");
        assertThat(square.width()).isEqualTo(768);
        assertThat(square.height()).isEqualTo(768);
    }

    @Test
    @DisplayName("잘못된 이미지 크기 설정은 애플리케이션 시작 전에 거부한다")
    void invalidDimension_IsRejected() {
        assertThatThrownBy(() -> new ImageGenerationPolicy(
                "flux", 32, 576, 768, true, "uctale-charcoal-v1", new SecureRandom()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
