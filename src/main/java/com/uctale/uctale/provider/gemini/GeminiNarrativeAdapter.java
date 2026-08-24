package com.uctale.uctale.provider.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uctale.uctale.application.narrative.NarrativeContext;
import com.uctale.uctale.application.narrative.NarrativeGenerator;
import com.uctale.uctale.application.narrative.NarrativeTurn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GeminiNarrativeAdapter implements NarrativeGenerator {

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    private static final String SYSTEM_INSTRUCTION = """
            당신은 UCTale의 Narrative Engine입니다.
            게임의 결정적 상태와 사실은 서버가 소유하며, 당신은 전달받은 상태를 바탕으로 서사를 표현합니다.

            [핵심 원칙]
            1. **캐논 우선:** [캐논 사실]과 [현재 게임 상태]에 반하는 내용을 진실로 확정하지 마십시오.
            2. **상태 비소유:** 능력치, 아이템, 생사, 위치 등 결정적 게임 상태를 임의로 변경했다고 확정하지 마십시오.
            3. **개연성:** 사건은 전달받은 과거 문맥과 사용자 행동에 맞는 인과관계를 가져야 합니다.
            4. **메모리 우선순위:** 캐논 사실 > 누적 요약 > 최근 턴 순으로 충돌을 해결하십시오.

            [시각적 요소(visual_assets) 작성 규칙 - 매우 중요]
            1. **이미지 생성 판단:** 직전 턴과 비교하여 **시각적으로 명확한 변화**가 있을 때만 작성하세요.
               - (O) 장소 이동, 새로운 적/NPC 등장, 중요한 아이템 등장
               - (X) 단순 대화, 생각, 시각적 변화가 없는 행동
            2. **변화가 없다면:** `background`, `characters`, `assets` 모든 필드를 비워두세요 (빈 문자열 "" 또는 빈 리스트 []).
            3. **작성 내용:**
               - `background`: 현재 장소나 분위기
               - `characters`: **주인공을 제외한** 등장인물, 몬스터
               - `assets`: 현재 상호작용 중인 핵심 사물
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

    public GeminiNarrativeAdapter(ObjectMapper objectMapper, RestClient.Builder builder) {
        this.objectMapper = objectMapper;
        this.restClient = builder.build();
    }

    @Override
    public NarrativeTurn createOpening(String worldSetting, String characterSetting) {
        try {
            String prompt = String.format("""
                    [세계관 설정]: %s
                    [캐릭터 설정]: %s

                    위 설정을 바탕으로 게임의 오프닝을 생성하세요.
                    첫 장면이므로 visual_assets(배경, 분위기 등)를 반드시 상세하게 채워주세요.
                    """, worldSetting, characterSetting);
            return generate(prompt, "Gemini API Error");
        } catch (Exception e) {
            log.error("Gemini 오프닝 생성 실패: {}", e.getClass().getSimpleName());
            throw new RuntimeException("AI 서버 연결 실패: 잠시 후 다시 시도해주세요.", e);
        }
    }

    @Override
    public NarrativeTurn createNextTurn(NarrativeContext context) {
        try {
            String prompt = String.format("""
                    [현재 게임 상태]
                    세계관: %s
                    플레이어: %s

                    [캐논 사실]
                    %s

                    [누적 요약]
                    %s

                    [최근 턴]
                    %s

                    [이번 사용자 행동]
                    %s

                    행동의 결과와 다음 상황을 서술하세요.
                    서버가 전달하지 않은 결정적 상태 변화는 임의로 확정하지 마세요.
                    시각적 변화가 없다면 visual_assets를 비워두세요.
                    """,
                    context.worldPremise(),
                    context.playerDescription(),
                    objectMapper.writeValueAsString(context.canonicalFacts()),
                    context.rollingSummary().isBlank() ? "(없음)" : context.rollingSummary(),
                    objectMapper.writeValueAsString(context.recentTurns()),
                    context.playerAction()
            );
            return generate(prompt, "Gemini API Progress Error");
        } catch (Exception e) {
            log.error("Gemini 다음 턴 생성 실패: {}", e.getClass().getSimpleName());
            throw new RuntimeException("AI 서버 연결 실패 (진행 중): 잠시 후 다시 시도해주세요.", e);
        }
    }

    private NarrativeTurn generate(String prompt, String errorContext) throws JsonProcessingException {
        String requestBody = createRequestBody(prompt);
        String response = restClient.post()
                .uri(GEMINI_API_URL + "?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);
        if (response == null || response.isBlank()) {
            throw new IllegalStateException(errorContext + ": empty response");
        }
        return parseResponse(response);
    }

    private String createRequestBody(String userPrompt) throws JsonProcessingException {
        Map<String, Object> requestMap = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", SYSTEM_INSTRUCTION + "\n\n" + userPrompt)))),
                "generationConfig", Map.of("response_mime_type", "application/json")
        );
        return objectMapper.writeValueAsString(requestMap);
    }

    private NarrativeTurn parseResponse(String rawResponse) throws JsonProcessingException {
        GeminiApiResponse apiResponse = objectMapper.readValue(rawResponse, GeminiApiResponse.class);
        if (apiResponse.candidates() == null || apiResponse.candidates().isEmpty()) {
            throw new IllegalStateException("AI 응답이 비어있습니다.");
        }

        String jsonText = apiResponse.candidates().get(0).content().parts().get(0).text();
        JsonNode rootNode = objectMapper.readTree(jsonText);

        List<NarrativeTurn.Choice> choices = new ArrayList<>();
        JsonNode choicesNode = rootNode.path("choices");
        if (choicesNode.isArray()) {
            int index = 1;
            for (JsonNode node : choicesNode) {
                if (node.isTextual()) {
                    choices.add(new NarrativeTurn.Choice(index++, node.asText()));
                } else if (node.isObject()) {
                    int id = node.has("id") ? node.get("id").asInt() : index++;
                    String text = node.has("text") ? node.get("text").asText() : "내용 없음";
                    choices.add(new NarrativeTurn.Choice(id, text));
                }
            }
        }

        JsonNode visualNode = rootNode.path("visual_assets");
        return new NarrativeTurn(
                rootNode.path("title").asText("제목 없음"),
                rootNode.path("story_text").asText("스토리가 없습니다."),
                choices,
                new NarrativeTurn.VisualAssets(
                        visualNode.path("background").asText(""),
                        readNonBlankStrings(visualNode.path("characters")),
                        readNonBlankStrings(visualNode.path("assets"))
                )
        );
    }

    private List<String> readNonBlankStrings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText();
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private record GeminiApiResponse(List<Candidate> candidates) {
        record Candidate(Content content) {}
        record Content(List<Part> parts) {}
        record Part(String text) {}
    }
}
