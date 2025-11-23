package com.uctale.uctale.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "pollinations.token=TEST_TOKEN")
class NanoBananaServiceTest {

    @Autowired
    private NanoBananaService nanoBananaService;

    @Test
    @DisplayName("이미지 생성 요청 시, Pollinations URL 형식을 반환해야 한다")
    void generateImage_Success() {
        // given
        String prompt = "zombie";
        String aspectRatio = "16:9";

        // when
        String resultUrl = nanoBananaService.generateImage(prompt, aspectRatio);

        // then
        // 1. Pollinations 도메인 확인
        assertThat(resultUrl).startsWith("https://image.pollinations.ai/prompt/");

        // 2. 프롬프트가 포함되었는지 확인 (인코딩된 상태)
        assertThat(resultUrl).contains("zombie");

        // 3. 해상도(16:9) 파라미터 확인 (변경된 768x432)
        assertThat(resultUrl).contains("width=768");
        assertThat(resultUrl).contains("height=432");

        // 4. 스타일 접미사(charcoal)가 포함되었는지 확인
        assertThat(resultUrl).contains("charcoal");

        // 5. 모델(flux) 파라미터 확인
        assertThat(resultUrl).contains("model=flux");

        // 6. 토큰 파라미터 확인
        assertThat(resultUrl).contains("token=TEST_TOKEN");
    }

    @Test
    @DisplayName("기본 비율(1:1) 요청 시 512x512 해상도를 반환해야 한다")
    void generateImage_DefaultRatio() {
        // given
        String prompt = "test";
        String aspectRatio = "1:1"; // 또는 다른 값

        // when
        String resultUrl = nanoBananaService.generateImage(prompt, aspectRatio);

        // then
        assertThat(resultUrl).contains("width=512");
        assertThat(resultUrl).contains("height=512");
    }
}