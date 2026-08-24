package com.uctale.uctale.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class NanoBananaServiceTest {

    private NanoBananaService nanoBananaService;

    @BeforeEach
    void setUp() {
        nanoBananaService = new NanoBananaService(RestClient.builder());
        ReflectionTestUtils.setField(nanoBananaService, "pollinationsToken", "TEST_SECRET_TOKEN");
    }

    @Test
    @DisplayName("게임 응답용 이미지 경로에는 Pollinations 토큰이 포함되지 않는다")
    void generateImage_DoesNotExposeProviderToken() {
        String resultUrl = nanoBananaService.generateImage("zombie in dark street", "16:9");

        assertThat(resultUrl).startsWith("/api/game/image?");
        assertThat(resultUrl).contains("prompt=zombie+in+dark+street");
        assertThat(resultUrl).contains("aspectRatio=16%3A9");
        assertThat(resultUrl).doesNotContain("TEST_SECRET_TOKEN");
        assertThat(resultUrl).doesNotContain("key=");
        assertThat(resultUrl).doesNotContain("pollinations.ai");
    }

    @Test
    @DisplayName("지원하지 않는 비율은 16대9 프록시 경로로 정규화한다")
    void generateImage_NormalizesAspectRatio() {
        String resultUrl = nanoBananaService.generateImage("test", "unknown");

        assertThat(resultUrl).contains("aspectRatio=16%3A9");
    }

    @Test
    @DisplayName("빈 프롬프트는 이미지 생성을 요청하지 않는다")
    void generateImage_BlankPrompt() {
        assertThat(nanoBananaService.generateImage(" ", "16:9")).isNull();
    }
}