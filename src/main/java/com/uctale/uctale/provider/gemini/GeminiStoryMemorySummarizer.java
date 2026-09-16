package com.uctale.uctale.provider.gemini;

import com.uctale.uctale.application.narrative.StoryMemorySummarizer;
import com.uctale.uctale.application.narrative.StoryMemorySummaryDraft;
import com.uctale.uctale.domain.game.GameTurn;
import com.uctale.uctale.domain.game.StorySummary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
public final class GeminiStoryMemorySummarizer implements StoryMemorySummarizer {

    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "OBJECT",
            "required", List.of("source_from_turn", "source_to_turn", "state_version", "text", "referenced_canonical_keys"),
            "properties", Map.of(
                    "source_from_turn", Map.of("type", "INTEGER", "minimum", 1),
                    "source_to_turn", Map.of("type", "INTEGER", "minimum", 1),
                    "state_version", Map.of("type", "INTEGER", "minimum", 1),
                    "text", Map.of("type", "STRING"),
                    "referenced_canonical_keys", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"))
            )
    );

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final GeminiProviderSettings settings;

    public GeminiStoryMemorySummarizer(ObjectMapper objectMapper, RestClient.Builder builder, GeminiProviderSettings settings) {
        this.objectMapper = objectMapper;
        this.restClient = builder.build();
        this.settings = settings;
    }

    @Override
    public StoryMemorySummaryDraft summarize(StorySummary previousSummary, List<GameTurn> sourceTurns, int stateVersion) {
        if (sourceTurns == null || sourceTurns.isEmpty()) throw new IllegalArgumentException("summary source turn은 필수입니다.");
        int sourceFrom = previousSummary == null || previousSummary.emptySummary()
                ? sourceTurns.getFirst().turnNumber() : previousSummary.sourceFromTurn();
        int sourceTo = sourceTurns.getLast().turnNumber();
        try {
            String prompt = """
                    UCTale Story Memory 요약기입니다. 입력 transcript에서 장기 서사에 필요한 비결정적 narrative 기억만 압축하세요.
                    HP/MP, inventory/equipment, quest/objective/flag, NPC affinity/stage, combat/ability처럼 GameState가 소유하는 사실은 summary에 저장하지 마세요.
                    NPC의 말투/약속/비밀, 장면의 정서적 맥락, 아직 canonical rule state가 아닌 서사적 단서만 보존할 수 있습니다.
                    이전 summary가 있으면 새 source turn을 반영해 갱신하되 모순을 만들지 마세요.
                    referenced_canonical_keys에는 summary가 의존한 stable canonical key를 모두 적으세요. canonical state 사실을 쓰지 않았다면 빈 배열입니다.
                    source_from_turn=%d, source_to_turn=%d, state_version=%d를 그대로 반환하세요.

                    [이전 summary]
                    %s

                    [새 source turns]
                    %s
                    """.formatted(sourceFrom, sourceTo, stateVersion,
                    previousSummary == null || previousSummary.emptySummary() ? "(없음)" : previousSummary.text(),
                    objectMapper.writeValueAsString(sourceTurns));
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of(
                            "responseMimeType", "application/json",
                            "responseSchema", RESPONSE_SCHEMA,
                            "thinkingConfig", settings.thinkingConfig(settings.progressThinkingLevel())
                    )
            );
            String response = restClient.post()
                    .uri(settings.generateContentUrl())
                    .header("x-goog-api-key", settings.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            return parse(response);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Story Memory summary 직렬화에 실패했습니다.", exception);
        }
    }

    private StoryMemorySummaryDraft parse(String response) throws JacksonException {
        if (response == null || response.isBlank()) throw new IllegalStateException("Story Memory summary 응답이 비어 있습니다.");
        JsonNode root = objectMapper.readTree(response);
        JsonNode candidates = root.get("candidates");
        if (candidates == null || !candidates.isArray() || candidates.isEmpty()) throw new IllegalStateException("Story Memory summary candidate가 없습니다.");
        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) throw new IllegalStateException("Story Memory summary content가 없습니다.");
        String text = parts.get(0).path("text").asText("");
        if (text.isBlank()) throw new IllegalStateException("Story Memory summary text가 없습니다.");
        JsonNode value = objectMapper.readTree(text);
        return new StoryMemorySummaryDraft(
                value.path("source_from_turn").asInt(0),
                value.path("source_to_turn").asInt(0),
                value.path("state_version").asInt(0),
                value.path("text").asText(""),
                objectMapper.convertValue(value.path("referenced_canonical_keys"), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
        );
    }
}
