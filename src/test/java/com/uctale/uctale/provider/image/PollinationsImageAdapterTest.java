package com.uctale.uctale.provider.image;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class PollinationsImageAdapterTest {

    private PollinationsImageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PollinationsImageAdapter(RestClient.builder());
        ReflectionTestUtils.setField(adapter, "pollinationsToken", "TEST_SECRET_TOKEN");
    }

    @Test
    @DisplayName("게임 응답용 이미지 경로에는 Pollinations 토큰이 포함되지 않는다")
    void createPublicUrl_DoesNotExposeProviderToken() {
        String resultUrl = adapter.createPublicUrl("zombie in dark street", "16:9");

        assertThat(resultUrl).startsWith("/api/game/image?");
        assertThat(resultUrl).contains("prompt=zombie+in+dark+street");
        assertThat(resultUrl).contains("aspectRatio=16%3A9");
        assertThat(resultUrl).doesNotContain("TEST_SECRET_TOKEN");
        assertThat(resultUrl).doesNotContain("key=");
        assertThat(resultUrl).doesNotContain("pollinations.ai");
    }

    @Test
    @DisplayName("지원하지 않는 비율은 16대9 프록시 경로로 정규화한다")
    void createPublicUrl_NormalizesAspectRatio() {
        String resultUrl = adapter.createPublicUrl("test", "unknown");

        assertThat(resultUrl).contains("aspectRatio=16%3A9");
    }

    @Test
    @DisplayName("빈 프롬프트는 이미지 생성을 요청하지 않는다")
    void createPublicUrl_BlankPrompt() {
        assertThat(adapter.createPublicUrl(" ", "16:9")).isNull();
    }
}
