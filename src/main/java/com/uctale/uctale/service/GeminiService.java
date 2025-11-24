package com.uctale.uctale.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GeminiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GeminiService {

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    // [수정] 이미지 생성 제어 및 프롬프트 조합을 위한 시스템 프롬프트 강화
    private static final String SYSTEM_INSTRUCTION = """
            당신은 텍스트 어드벤처 게임 마스터(GM)입니다.
            사용자가 입력한 [세계관]과 [캐릭터] 설정을 절대적인 진실로 받아들이고, 이를 바탕으로 일관성 있는 스토리를 진행하세요.
            
            [핵심 원칙]
            1. **세계관 준수:** 현실/판타지/SF 등 사용자가 설정한 장르를 엄격히 따르십시오.
            2. **개연성:** 사건은 인과관계에 맞게 발생해야 합니다.
            
            [시각적 요소(visual_assets) 작성 규칙 - 매우 중요]
            1. **이미지 생성 판단:** 직전 턴과 비교하여 **시각적으로 명확한 변화**가 있을 때만 작성하세요.
               - (O) 장소 이동, 새로운 적/NPC 등장, 중요한 아이템 획득
               - (X) 단순 대화, 생각, 시각적 변화가 없는 행동
            2. **변화가 없다면:** `background`, `characters`, `assets` 모든 필드를 비워두세요 (빈 문자열 "" 또는 빈 리스트 []).
            3. **작성 내용:**
               - `background`: 현재 장소나 분위기 (예: 'dark abandoned subway station')
               - `characters`: **주인공을 제외한** 등장인물, 몬스터 (예: 'bloody zombie', 'angry soldier')
               - `assets`: 현재 상호작용 중인 핵심 사물 (예: 'red fire extinguisher', 'rusty old key')
               - 모든 묘사는 **영어(English)**로 작성해야 합니다.
            
            [JSON 응답 형식]
            {
              "title": "string (한국어)",
              "story_text": "string (한국어, 3~5문장)",
              "choices": [
                { "id": 1, "text": "행동 1" },
                { "id": 2, "text": "행동 2" }
              ],
              "visual_assets": {
                "background": "string (English or empty)",
                "characters": ["string (English or empty)"],
                "assets": ["string (English or empty)"]
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
            [세계관 설정]: %s
            [캐릭터 설정]: %s
            
            위 설정을 바탕으로 게임의 오프닝을 생성하세요.
            첫 장면이므로 visual_assets(배경, 분위기 등)를 반드시 상세하게 채워주세요.
            """, request.worldSetting(), request.characterSetting());
    }

    private String createProgressPrompt(String world, String character, String previousStory, String userChoice) {
        return String.format("""
            [세계관]: %s
            [캐릭터]: %s
            [직전 상황]: %s
            [사용자 행동]: %s
            
            1. 행동에 대한 결과를 서술하고 다음 상황을 제시하세요.
            2. 시각적 변화가 없다면 visual_assets를 비워두어 불필요한 이미지 생성을 막으세요.
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
        JsonNode rootNode = objectMapper.readTree(jsonText);

        String title = rootNode.path("title").asText("제목 없음");
        String storyText = rootNode.path("story_text").asText("스토리가 없습니다.");

        List<GeminiResponse.Choice> choices = new ArrayList<>();
        JsonNode choicesNode = rootNode.path("choices");

        if (choicesNode.isArray()) {
            int index = 1;
            for (JsonNode node : choicesNode) {
                if (node.isTextual()) {
                    choices.add(new GeminiResponse.Choice(index++, node.asText()));
                } else if (node.isObject()) {
                    int id = node.has("id") ? node.get("id").asInt() : index++;
                    String text = node.has("text") ? node.get("text").asText() : "내용 없음";
                    choices.add(new GeminiResponse.Choice(id, text));
                }
            }
        }

        JsonNode visualNode = rootNode.path("visual_assets");
        String background = visualNode.path("background").asText("");

        // [수정] 빈 문자열은 리스트에 추가하지 않도록 필터링하여 정확한 생략 로직 지원
        List<String> characters = new ArrayList<>();
        if (visualNode.has("characters") && visualNode.get("characters").isArray()) {
            for (JsonNode n : visualNode.get("characters")) {
                String val = n.asText();
                if (val != null && !val.isBlank()) characters.add(val);
            }
        }

        List<String> assets = new ArrayList<>();
        if (visualNode.has("assets") && visualNode.get("assets").isArray()) {
            for (JsonNode n : visualNode.get("assets")) {
                String val = n.asText();
                if (val != null && !val.isBlank()) assets.add(val);
            }
        }

        GeminiResponse.VisualAssets visualAssets = new GeminiResponse.VisualAssets(background, characters, assets);

        return new GeminiResponse(title, storyText, choices, visualAssets);
    }

    private record GeminiApiResponse(List<Candidate> candidates) {
        record Candidate(Content content) {}
        record Content(List<Part> parts) {}
        record Part(String text) {}
    }
}