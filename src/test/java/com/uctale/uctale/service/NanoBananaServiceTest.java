package com.uctale.uctale.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "pollinations.token=TEST_TOKEN",
        "game.access.password=TEST_PASSWORD"
})
class NanoBananaServiceTest {

    @Autowired
    private NanoBananaService nanoBananaService;

    @Test
    @DisplayName("이미지 생성 요청 시 현재 Pollinations URL 형식을 반환해야 한다")
    void generateImage_Success() {
        String resultUrl = nanoBananaService.generateImage("zombie", "16:9");

        assertThat(resultUrl).startsWith("https://gen.pollinations.ai/image/");
        assertThat(resultUrl).contains("zombie");
        assertThat(resultUrl).contains("width=768");
        assertThat(resultUrl).contains("height=432");
        assertThat(resultUrl).contains("charcoal");
        assertThat(resultUrl).contains("model=flux");
        assertThat(resultUrl).contains("key=TEST_TOKEN");
    }

    @Test
    @DisplayName("기본 비율 요청 시 512x512 해상도를 반환해야 한다")
    void generateImage_DefaultRatio() {
        String resultUrl = nanoBananaService.generateImage("test", "1:1");

        assertThat(resultUrl).contains("width=512");
        assertThat(resultUrl).contains("height=512");
    }
}
