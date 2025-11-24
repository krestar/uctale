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

    // [수정] 세계관 일관성 및 장르 제약 조건을 강력하게 추가한 시스템 프롬프트
    private static final String SYSTEM_INSTRUCTION = """
            당신은 텍스트 어드벤처 게임 마스터(GM)입니다.
            사용자가 입력한 [세계관]과 [캐릭터] 설정을 절대적인 진실로 받아들이고, 이를 바탕으로 일관성 있는 스토리를 진행하세요.
            
            [핵심 원칙 - 장르 일관성 유지]
            1. **세계관 분석:** 사용자의 세계관이 '현실 기반(전쟁, 재난, 조난)'인지 '판타지/SF(좀비, 몬스터, 마법)'인지 먼저 파악하세요.
            2. **현실 기반일 경우:** 좀비, 몬스터, 유령 등 초자연적 존재를 **절대** 등장시키지 마세요. 대신 약탈자, 야생 동물, 군인, 자연재해 등 현실적인 위협을 등장시키세요.
            3. **판타지일 경우:** 세계관에 어울리는 크리처를 등장시키세요.
            4. **개연성:** 사건은 인과관계에 맞게 발생해야 합니다. 갑작스러운 장르 변경을 금지합니다.
            
            [작성 규칙]
            1. **언어:** 'title', 'story_text', 'choices'는 한국어, 'visual_assets'는 영어로 작성하세요.
            2. **제목:** 상황에 어울리는 제목을 유지하거나 갱신하세요.
            3. **스토리:** 3~5문장 내외로, 현재 상황과 사용자의 행동에 대한 결과를 생생하게 묘사하세요.
            4. **시각적 요소:** - 'visual_assets'에는 현재 장면을 묘사하는 키워드를 넣으세요.
               - 현실적인 상황이라면 몬스터 대신 'angry robber holding a knife', 'ruined city streets' 등을 묘사해야 합니다.
            
            [JSON 응답 형식]
            {
              "title": "string",
              "story_text": "string",
              "choices": [
                { "id": 1, "text": "행동 1" },
                { "id": 2, "text": "행동 2" },
                { "id": 3, "text": "행동 3" }
              ],
              "visual_assets": {
                "background": "string (English prompt)",
                "characters": ["string (English prompt)"],
                "assets": ["string (English prompt)"]
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
            
            위 설정을 철저히 준수하여 오프닝을 생성하세요.
            세계관에 명시되지 않은 장르(예: 갑작스러운 판타지 요소)를 섞지 마십시오.
            """, request.worldSetting(), request.characterSetting());
    }

    private String createProgressPrompt(String world, String character, String previousStory, String userChoice) {
        return String.format("""
            [세계관]: %s
            [캐릭터]: %s
            [직전 상황]: %s
            [사용자 행동]: %s
            
            1. 사용자의 행동에 대한 결과를 서술하고 다음 상황을 제시하세요.
            2. [세계관]의 장르적 특성을 위배하지 마십시오. (예: 현실 재난물에 몬스터 등장 금지)
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

        List<String> characters = new ArrayList<>();
        if (visualNode.has("characters") && visualNode.get("characters").isArray()) {
            for (JsonNode n : visualNode.get("characters")) characters.add(n.asText());
        }

        List<String> assets = new ArrayList<>();
        if (visualNode.has("assets") && visualNode.get("assets").isArray()) {
            for (JsonNode n : visualNode.get("assets")) assets.add(n.asText());
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