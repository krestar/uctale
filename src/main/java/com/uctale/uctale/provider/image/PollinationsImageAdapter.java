package com.uctale.uctale.provider.image;

import com.uctale.uctale.application.image.ImageGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class PollinationsImageAdapter implements ImageGenerator {

    private static final String STYLE_SUFFIX = ", rough charcoal sketch, high contrast black and white, gritty texture, white background, pencil drawing style, no colors, concept art";
    private static final String PROVIDER_BASE_URL = "https://gen.pollinations.ai/image/";

    private final RestClient restClient;

    @Value("${pollinations.token}")
    private String pollinationsToken;

    public PollinationsImageAdapter(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String createPublicUrl(String prompt, String aspectRatio) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }

        String encodedPrompt = URLEncoder.encode(prompt, StandardCharsets.UTF_8);
        String encodedAspectRatio = URLEncoder.encode(normalizeAspectRatio(aspectRatio), StandardCharsets.UTF_8);
        return "/api/game/image?prompt=" + encodedPrompt + "&aspectRatio=" + encodedAspectRatio;
    }

    @Override
    public GeneratedImage fetchImage(String prompt, String aspectRatio) {
        try {
            ResponseEntity<byte[]> response = restClient.get()
                    .uri(buildProviderUrl(prompt, aspectRatio))
                    .retrieve()
                    .toEntity(byte[].class);

            MediaType contentType = response.getHeaders().getContentType();
            if (contentType == null) {
                contentType = MediaType.IMAGE_JPEG;
            }
            return new GeneratedImage(response.getBody(), contentType);
        } catch (Exception e) {
            log.error("Pollinations 이미지 생성 요청 실패: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private String buildProviderUrl(String prompt, String aspectRatio) {
        String fullPrompt = prompt + STYLE_SUFFIX;
        String encodedPrompt = URLEncoder.encode(fullPrompt, StandardCharsets.UTF_8);
        String sizeParam = "1:1".equals(normalizeAspectRatio(aspectRatio))
                ? "width=512&height=512"
                : "width=768&height=432";
        String tokenParam = pollinationsToken == null || pollinationsToken.isBlank()
                ? ""
                : "&key=" + URLEncoder.encode(pollinationsToken, StandardCharsets.UTF_8);

        return PROVIDER_BASE_URL + encodedPrompt + "?" + sizeParam + "&nologo=true&model=flux" + tokenParam;
    }

    private String normalizeAspectRatio(String aspectRatio) {
        return "1:1".equals(aspectRatio) ? "1:1" : "16:9";
    }
}
