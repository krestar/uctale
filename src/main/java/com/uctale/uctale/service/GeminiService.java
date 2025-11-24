package com.uctale.uctale.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GeminiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GeminiService {

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    private static final String SYSTEM_INSTRUCTION = """
            당신은 텍스트 어드벤처 게임 마스터(GM)입니다.
            사용자가 입력한 세계관과 캐릭터를 바탕으로 몰입감 넘치는 오프닝 스토리를 창작하세요.
            
            [작성 규칙]
            1. **언어 규칙:** 'story_text'와 'choices'는 한국어, 'visual_assets'는 영어로 작성하세요.
            2. **세계관 확장:** 사용자의 입력에 디테일을 더해 세계관을 풍성하게 만드세요.
            3. **스토리 전개:** 장르에 맞는 흥미진진한 도입부(3~5문장)를 만드세요.
            4. **시각적 요소:** 배경(background)과 등장인물(characters) 묘사를 영어로 작성하세요.
            
            [JSON 응답 형식]
            {
              "story_text": "string (한국어)",
              "choices": [{"id": 1, "text": "..."}, {"id": 2, "text": "..."}, {"id": 3, "text": "..."}],
              "visual_assets": {
                "background": "string (English prompt)",
                "characters": ["string (English prompt)"],
                "assets": []
              }
            }
            """;

    @Value("${google.ai.api-key}")
    private String apiKey;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiService(ObjectMapper objectMapper, RestClient.Builder builder) {
        this.objectMapper = objectMapper;
        this.restClient = builder.build();
    }

    public GeminiResponse getOpening(GameInitRequest request) {
        try {
            String requestBody = createRequestBody(createOpeningPrompt(request));
            String response = callGeminiApi(requestBody);
            return parseGeminiResponse(response);
        } catch (Exception e) {
            log.error("Gemini API Error: {}", e.getMessage());
            throw new RuntimeException("AI 서버 연결 실패: 잠시 후 다시 시도해주세요.", e);
        }
    }

    public GeminiResponse getNextTurn(String world, String character, String previousStory, String userChoice) {
        try {
            String prompt = createProgressPrompt(world, character, previousStory, userChoice);
            String requestBody = createRequestBody(prompt);
            String response = callGeminiApi(requestBody);
            return parseGeminiResponse(response);
        } catch (Exception e) {
            log.error("Gemini API Progress Error: {}", e.getMessage());
            throw new RuntimeException("AI 서버 연결 실패 (진행 중): 잠시 후 다시 시도해주세요.", e);
        }
    }

    private String callGeminiApi(String requestBody) {
        return restClient.post()
                .uri(GEMINI_API_URL + "?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);
    }

    private String createOpeningPrompt(GameInitRequest request) {
        return String.format("""
            [사용자 입력 세계관]: %s
            [사용자 캐릭터 설정]: %s
            위 설정을 바탕으로 게임의 오프닝을 생성하세요.
            """, request.worldSetting(), request.characterSetting());
    }

    private String createProgressPrompt(String world, String character, String previousStory, String userChoice) {
        return String.format("""
            [세계관]: %s
            [캐릭터]: %s
            [이전 스토리]: %s
            [사용자의 선택]: %s
            
            위 선택에 따른 다음 스토리를 진행하고, 새로운 선택지 3개를 제시하세요.
            시각적으로 보여줄 만한 새로운 배경이나 등장인물이 있다면 visual_assets에 영어로 묘사하세요.
            (변화가 없다면 visual_assets는 비워두거나 이전과 동일하게 유지해도 됩니다.)
            """, world, character, previousStory, userChoice);
    }

    private String createRequestBody(String userPrompt) throws JsonProcessingException {
        Map<String, Object> requestMap = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", SYSTEM_INSTRUCTION + "\n\n" + userPrompt)))),
                "generationConfig", Map.of("response_mime_type", "application/json")
        );
        return objectMapper.writeValueAsString(requestMap);
    }

    private GeminiResponse parseGeminiResponse(String rawResponse) throws JsonProcessingException {
        GeminiApiResponse apiResponse = objectMapper.readValue(rawResponse, GeminiApiResponse.class);
        if (apiResponse.candidates() == null || apiResponse.candidates().isEmpty()) {
            throw new RuntimeException("AI 응답이 비어있습니다.");
        }
        String jsonText = apiResponse.candidates().get(0).content().parts().get(0).text();
        return objectMapper.readValue(jsonText, GeminiResponse.class);
    }

    private record GeminiApiResponse(List<Candidate> candidates) {
        record Candidate(Content content) {}
        record Content(List<Part> parts) {}
        record Part(String text) {}
    }
}