package com.uctale.uctale.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class NanoBananaService {

    @Value("${pollinations.token}")
    private String pollinationsToken;

    private static final String STYLE_SUFFIX = ", rough charcoal sketch, high contrast black and white, gritty texture, white background, pencil drawing style, no colors, concept art";

    public NanoBananaService() {
    }

    public String generateImage(String prompt, String aspectRatio) {
        try {
            String fullPrompt = prompt + STYLE_SUFFIX;

            String encodedPrompt = URLEncoder.encode(fullPrompt, StandardCharsets.UTF_8);

            String sizeParam = "width=512&height=512";
            if ("16:9".equals(aspectRatio)) {
                sizeParam = "width=768&height=432";
            }

            String tokenParam = (pollinationsToken != null && !pollinationsToken.isBlank())
                    ? "&token=" + pollinationsToken
                    : "";

            return String.format("https://image.pollinations.ai/prompt/%s?%s&nologo=true&model=flux%s", encodedPrompt, sizeParam, tokenParam);

        } catch (Exception e) {
            log.error("이미지 URL 생성 실패: {}", e.getMessage());
            return null;
        }
    }
}