package com.uctale.uctale.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class NanoBananaService {

    @Value("${google.ai.api-key}")
    private String apiKey;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    // 최신 Gemini 이미지 모델 엔드포인트
    private static final String IMAGE_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent";

    // 스케치 스타일 이미지 생성 프롬프트 하드코딩
    private static final String STYLE_SUFFIX = ", rough charcoal sketch, high contrast black and white, gritty texture, white background, pencil drawing style, no colors, concept art";

    // 테스트 가능하도록 Builder 주입
    public NanoBananaService(ObjectMapper objectMapper, RestClient.Builder builder) {
        this.objectMapper = objectMapper;
        this.restClient = builder.build();
    }

    public String generateImage(String prompt, String aspectRatio) {
        try {
            String fullPrompt = prompt + STYLE_SUFFIX;
            String requestBody = createRequestBody(fullPrompt, aspectRatio);

            String response = restClient.post()
                    .uri(IMAGE_API_URL + "?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseImageResponse(response);

        } catch (Exception e) {
            log.error("이미지 생성 실패: {}", e.getMessage());
            return null; // 이미지가 없어도 게임은 진행됨
        }
    }

    private String createRequestBody(String prompt, String aspectRatio) throws JsonProcessingException {
        // 와이드 비율 요청 시 프롬프트에 힌트 추가
        String ratioText = aspectRatio.equals("16:9") ? " (Wide angle 16:9 aspect ratio)" : "";
        String finalPrompt = prompt + ratioText;

        // Gemini REST API 요청 구조
        Map<String, Object> requestMap = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", finalPrompt)
                        ))
                ),
                "generationConfig", Map.of(
                        "response_mime_type", "image/jpeg"
                )
        );
        return objectMapper.writeValueAsString(requestMap);
    }

    private String parseImageResponse(String rawResponse) throws JsonProcessingException {
        // Gemini 응답 구조: candidates[0].content.parts[0].inline_data.data
        JsonNode root = objectMapper.readTree(rawResponse);

        try {
            JsonNode candidates = root.path("candidates");
            if (candidates.isMissingNode() || candidates.isEmpty()) {
                throw new RuntimeException("No candidates");
            }

            JsonNode parts = candidates.get(0).path("content").path("parts");
            for (JsonNode part : parts) {
                if (part.has("inline_data")) {
                    String base64Image = part.path("inline_data").path("data").asText();
                    // 프론트엔드용 Data URL 포맷으로 반환
                    return "data:image/jpeg;base64," + base64Image;
                }
            }
            throw new RuntimeException("이미지 데이터가 없습니다.");

        } catch (Exception e) {
            throw new RuntimeException("이미지 파싱 실패", e);
        }
    }
}