package com.uctale.uctale.provider.gemini;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.uctale.uctale.application.narrative.NarrativeContext;
import com.uctale.uctale.application.narrative.NarrativeGenerator;
import com.uctale.uctale.application.narrative.NarrativeTurn;
import com.uctale.uctale.application.narrative.RecoverableNarrativeResponseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class GeminiNarrativeAdapter implements NarrativeGenerator {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_STORY_LENGTH = 50_000;
    private static final int MAX_CHOICE_TEXT_LENGTH = 255;
    private static final int MAX_CHOICES = 8;
    private static final int MAX_VISUAL_ITEMS = 8;
    private static final int MAX_VISUAL_TEXT_LENGTH = 1_000;

    private static final String SYSTEM_INSTRUCTION = """
            당신은 UCTale의 Narrative Engine입니다.
            게임의 결정적 상태와 사실은 서버가 소유하며, 당신은 전달받은 확정 결과를 서사로 표현합니다.

            [핵심 원칙]
            1. **확정 결과 우선:** [확정 게임 결과], [확정 상태 projection], [캐논 사실]에 반하는 내용을 진실로 확정하지 마십시오.
            2. **상태 비소유:** 능력치, 아이템, 생사, 위치 등 결정적 게임 상태를 임의로 변경하거나 다시 판정하지 마십시오.
            3. **개연성:** 사건은 전달받은 결과, 과거 문맥, resolved action에 맞는 인과관계를 가져야 합니다.
            4. **메모리 우선순위:** 확정 게임 결과/상태 > 캐논 사실 > 누적 요약 > 최근 턴 순으로 충돌을 해결하십시오.
            5. **출력 책임:** story_text와 다음 choices를 제안하고, 선택적으로 visual_assets를 묘사하십시오. canonical state 변경값을 출력 계약으로 만들지 마십시오.

            [내러티브 출력 언어 계약 - 매우 중요]
            1. 사용자에게 노출되는 `title`, `story_text`, `choices[].text`는 세계관/캐릭터 설정과 현재 narrative context에서 확립된 **주 언어**를 따르세요.
            2. 세계관과 캐릭터 설정의 주 언어가 한국어라면 `title`, `story_text`, `choices[].text`를 모두 한국어로 작성하세요.
            3. JSON field 이름이나 아래 `visual_assets` 영어 계약 때문에 사용자 내러티브 언어를 영어로 바꾸거나 번역하지 마세요.
            4. opening, progress, repair 사이에서 사용자 내러티브의 주 언어를 임의로 전환하지 마세요.
            5. `visual_assets`는 이미지 provider용 별도 계약이며 사용자 내러티브 언어와 무관하게 영어로 작성합니다.

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

            JSON 구조는 API의 response schema를 반드시 따르세요.
            """;

    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "OBJECT",
            "required", List.of("title", "story_text", "choices"),
            "properties", Map.of(
                    "title", Map.of("type", "STRING"),
                    "story_text", Map.of("type", "STRING"),
                    "choices", Map.of(
                            "type", "ARRAY",
                            "minItems", 1,
                            "maxItems", MAX_CHOICES,
                            "items", Map.of(
                                    "type", "OBJECT",
                                    "required", List.of("id", "text"),
                                    "properties", Map.of(
                                            "id", Map.of("type", "INTEGER", "minimum", 1),
                                            "text", Map.of("type", "STRING")
                                    )
                            )
                    ),
                    "visual_assets", Map.of(
                            "type", "OBJECT",
                            "properties", Map.of(
                                    "background", Map.of("type", "STRING"),
                                    "characters", Map.of(
                                            "type", "ARRAY",
                                            "maxItems", MAX_VISUAL_ITEMS,
                                            "items", Map.of("type", "STRING")
                                    ),
                                    "assets", Map.of(
                                            "type", "ARRAY",
                                            "maxItems", MAX_VISUAL_ITEMS,
                                            "items", Map.of("type", "STRING")
                                    )
                            )
                    )
            )
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final GeminiProviderSettings settings;

    @Autowired
    public GeminiNarrativeAdapter(
            ObjectMapper objectMapper,
            RestClient.Builder builder,
            GeminiProviderSettings settings
    ) {
        this.objectMapper = objectMapper;
        this.restClient = builder.build();
        this.settings = settings;
    }

    @Override
    public NarrativeTurn createOpening(String worldSetting, String characterSetting) {
        return generate(
                openingPrompt(worldSetting, characterSetting),
                "opening",
                settings.openingThinkingLevel()
        );
    }

    @Override
    public NarrativeTurn repairOpening(String worldSetting, String characterSetting, String reasonCode) {
        return generate(
                openingPrompt(worldSetting, characterSetting) + recoveryInstruction(reasonCode),
                "opening_repair",
                settings.openingThinkingLevel()
        );
    }

    @Override
    public NarrativeTurn createNextTurn(NarrativeContext context) {
        try {
            return generate(buildProgressPrompt(context), "progress", settings.progressThinkingLevel());
        } catch (JacksonException exception) {
            throw new IllegalStateException("Narrative progress prompt 직렬화에 실패했습니다.", exception);
        }
    }

    @Override
    public NarrativeTurn repairNextTurn(NarrativeContext context, String reasonCode) {
        try {
            return generate(
                    buildProgressPrompt(context) + recoveryInstruction(reasonCode),
                    "progress_repair",
                    settings.progressThinkingLevel()
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("Narrative recovery prompt 직렬화에 실패했습니다.", exception);
        }
    }

    private String openingPrompt(String worldSetting, String characterSetting) {
        return String.format("""
                [세계관 설정]: %s
                [캐릭터 설정]: %s

                [내러티브 출력 언어]
                `title`, `story_text`, `choices[].text`는 위 세계관/캐릭터 설정의 주 언어로 작성하세요.
                주 언어가 한국어라면 해당 사용자 노출 필드를 모두 한국어로 작성하세요.
                `visual_assets`만 이미지 provider 계약에 따라 영어로 작성하세요.

                위 설정을 바탕으로 게임의 오프닝을 생성하세요.
                첫 장면이므로 visual_assets(배경, 분위기 등)를 반드시 상세하게 채워주세요.
                """, worldSetting, characterSetting);
    }

    private String recoveryInstruction(String reasonCode) {
        return """

                [응답 수정 요청]
                직전 provider 응답이 구조 계약을 만족하지 않았습니다.
                실패 분류: %s
                원래 게임 결과나 사실을 바꾸지 말고, 동일 요청을 response schema에 맞는 JSON으로 다시 작성하세요.
                원래 요청에서 확립된 `title`, `story_text`, `choices[].text`의 주 언어를 그대로 유지하고 번역하거나 다른 언어로 전환하지 마세요.
                `visual_assets`는 기존과 동일하게 영어로 작성하세요.
                """.formatted(safeReasonCode(reasonCode));
    }

    String buildProgressPrompt(NarrativeContext context) throws JacksonException {
        return String.format("""
                [확정 게임 결과 ID]
                %s

                [resolved action]
                %s

                [확정 게임 결과]
                outcome: %s
                skill check: %s
                canonical facts: %s
                events: %s
                state changes: %s

                [확정 상태 projection]
                %s

                [캐논 사실]
                %s

                [누적 요약]
                %s

                [최근 턴]
                %s

                [narrative cues]
                %s

                [금지 canonical mutation]
                %s

                [내러티브 출력 언어]
                `title`, `story_text`, `choices[].text`는 worldPremise, playerDescription과 누적/최근 narrative context에서 확립된 주 언어를 계속 사용하세요.
                해당 주 언어가 한국어라면 사용자 노출 내러티브 필드를 모두 한국어로 작성하세요.
                `visual_assets`만 이미지 provider 계약에 따라 영어로 작성하고, 그 영어 계약 때문에 사용자 내러티브 언어를 바꾸지 마세요.

                위 확정 결과를 변경하거나 재판정하지 말고 그 결과가 드러나는 장면을 서술하세요.
                Skill Check가 있으면 raw roll, modifier, DC, total, success/failure를 서버가 확정한 그대로 따르세요.
                서버가 확정하지 않은 roll, 성공/실패, HP/능력치/아이템/레벨/위치/생사 변화를 새 사실로 만들지 마세요.
                응답은 story 표현, 다음 choices 제안, 선택적 visual_assets만 포함합니다.
                시각적 변화가 없다면 visual_assets를 비워두세요.
                """,
                context.canonicalResultId(),
                objectMapper.writeValueAsString(context.resolvedAction()),
                context.outcome(),
                context.skillCheck() == null ? "(없음)" : objectMapper.writeValueAsString(context.skillCheck()),
                objectMapper.writeValueAsString(context.resultCanonicalFacts()),
                objectMapper.writeValueAsString(context.events()),
                objectMapper.writeValueAsString(context.stateChanges()),
                objectMapper.writeValueAsString(context.state()),
                objectMapper.writeValueAsString(context.memory().canonicalFacts()),
                context.memory().rollingSummary().isBlank() ? "(없음)" : context.memory().rollingSummary(),
                objectMapper.writeValueAsString(context.memory().recentTurns()),
                objectMapper.writeValueAsString(context.narrativeCues()),
                objectMapper.writeValueAsString(context.forbiddenCanonicalMutations())
        );
    }

    private NarrativeTurn generate(
            String prompt,
            String errorContext,
            GeminiProviderSettings.ThinkingLevel thinkingLevel
    ) {
        long startedAt = System.nanoTime();
        TokenUsage tokenUsage = TokenUsage.empty();
        try {
            String requestBody = createRequestBody(prompt, thinkingLevel);
            String response = restClient.post()
                    .uri(settings.generateContentUrl())
                    .header("x-goog-api-key", settings.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            if (response == null || response.isBlank()) {
                throw recoverable("EMPTY_RESPONSE", "Gemini 응답 body가 비어 있습니다.", null);
            }
            tokenUsage = readTokenUsage(response);
            NarrativeTurn turn = parseResponse(response);
            logProviderResult(errorContext, thinkingLevel, tokenUsage, startedAt, "SUCCESS");
            return turn;
        } catch (RecoverableNarrativeResponseException exception) {
            logProviderResult(errorContext, thinkingLevel, tokenUsage, startedAt, "INVALID_RESPONSE");
            log.warn("gemini_response_invalid context={} reason={}", errorContext, exception.reasonCode());
            throw exception;
        } catch (JacksonException exception) {
            logProviderResult(errorContext, thinkingLevel, tokenUsage, startedAt, "REQUEST_SERIALIZATION_FAILURE");
            throw new IllegalStateException("Gemini 요청 직렬화에 실패했습니다.", exception);
        } catch (RuntimeException exception) {
            logProviderResult(errorContext, thinkingLevel, tokenUsage, startedAt, "PROVIDER_FAILURE");
            log.error("Gemini provider 호출 실패 context={} error={}", errorContext, exception.getClass().getSimpleName());
            throw exception;
        }
    }

    String createRequestBody(
            String userPrompt,
            GeminiProviderSettings.ThinkingLevel thinkingLevel
    ) throws JacksonException {
        Map<String, Object> requestMap = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", SYSTEM_INSTRUCTION + "\n\n" + userPrompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", RESPONSE_SCHEMA,
                        "thinkingConfig", settings.thinkingConfig(thinkingLevel)
                )
        );
        return objectMapper.writeValueAsString(requestMap);
    }

    NarrativeTurn parseResponse(String rawResponse) {
        try {
            GeminiApiResponse apiResponse = objectMapper.readValue(rawResponse, GeminiApiResponse.class);
            if (apiResponse.candidates() == null || apiResponse.candidates().isEmpty()) {
                throw recoverable("MISSING_CANDIDATE", "Gemini candidate가 없습니다.", null);
            }
            Candidate candidate = apiResponse.candidates().getFirst();
            if (candidate == null || candidate.content() == null || candidate.content().parts() == null || candidate.content().parts().isEmpty()) {
                throw recoverable("MISSING_CONTENT", "Gemini content part가 없습니다.", null);
            }
            Part part = candidate.content().parts().getFirst();
            if (part == null || part.text() == null || part.text().isBlank()) {
                throw recoverable("MISSING_TEXT", "Gemini content text가 없습니다.", null);
            }

            JsonNode rootNode = objectMapper.readTree(part.text());
            return mapNarrative(rootNode);
        } catch (RecoverableNarrativeResponseException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw recoverable("MALFORMED_JSON", "Gemini JSON을 파싱할 수 없습니다.", exception);
        }
    }

    private TokenUsage readTokenUsage(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode usage = root == null ? null : root.get("usageMetadata");
            if (usage == null || !usage.isObject()) {
                return TokenUsage.empty();
            }
            return new TokenUsage(
                    optionalNonNegativeInt(usage.get("promptTokenCount")),
                    optionalNonNegativeInt(usage.get("candidatesTokenCount")),
                    optionalNonNegativeInt(usage.get("thoughtsTokenCount")),
                    optionalNonNegativeInt(usage.get("totalTokenCount"))
            );
        } catch (JacksonException ignored) {
            return TokenUsage.empty();
        }
    }

    private Integer optionalNonNegativeInt(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            return null;
        }
        int value = node.intValue();
        return value < 0 ? null : value;
    }

    private void logProviderResult(
            String context,
            GeminiProviderSettings.ThinkingLevel thinkingLevel,
            TokenUsage usage,
            long startedAt,
            String outcome
    ) {
        long latencyMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        log.info(
                "gemini_provider_result model={} thinkingLevel={} context={} latencyMs={} outcome={} promptTokens={} candidatesTokens={} thoughtsTokens={} totalTokens={}",
                settings.modelId(),
                thinkingLevel.apiValue(),
                context,
                latencyMs,
                outcome,
                usage.promptTokenCount(),
                usage.candidatesTokenCount(),
                usage.thoughtsTokenCount(),
                usage.totalTokenCount()
        );
    }

    private NarrativeTurn mapNarrative(JsonNode rootNode) {
        if (rootNode == null || !rootNode.isObject()) {
            throw recoverable("ROOT_NOT_OBJECT", "Narrative root는 object여야 합니다.", null);
        }

        String title = requiredText(rootNode, "title", MAX_TITLE_LENGTH, false, "INVALID_TITLE");
        String storyText = requiredText(rootNode, "story_text", MAX_STORY_LENGTH, true, "INVALID_STORY");

        JsonNode choicesNode = rootNode.get("choices");
        if (choicesNode == null || !choicesNode.isArray() || choicesNode.size() == 0 || choicesNode.size() > MAX_CHOICES) {
            throw recoverable("INVALID_CHOICES", "Narrative choices 배열이 올바르지 않습니다.", null);
        }

        List<NarrativeTurn.Choice> choices = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        for (JsonNode choiceNode : choicesNode) {
            if (choiceNode == null || !choiceNode.isObject()) {
                throw recoverable("INVALID_CHOICE", "Narrative choice는 object여야 합니다.", null);
            }
            JsonNode idNode = choiceNode.get("id");
            if (idNode == null || !idNode.isIntegralNumber() || !idNode.canConvertToInt()) {
                throw recoverable("INVALID_CHOICE_ID", "Narrative choice id가 정수가 아닙니다.", null);
            }
            int id = idNode.intValue();
            if (id <= 0 || !ids.add(id)) {
                throw recoverable("INVALID_CHOICE_ID", "Narrative choice id가 중복되었거나 범위를 벗어났습니다.", null);
            }
            String text = requiredText(choiceNode, "text", MAX_CHOICE_TEXT_LENGTH, false, "INVALID_CHOICE_TEXT");
            choices.add(new NarrativeTurn.Choice(id, text));
        }

        JsonNode visualNode = rootNode.get("visual_assets");
        NarrativeTurn.VisualAssets visualAssets = visualNode == null || visualNode.isNull()
                ? new NarrativeTurn.VisualAssets("", List.of(), List.of())
                : parseVisualAssets(visualNode);

        return new NarrativeTurn(title, storyText, List.copyOf(choices), visualAssets);
    }

    private NarrativeTurn.VisualAssets parseVisualAssets(JsonNode visualNode) {
        if (!visualNode.isObject()) {
            throw recoverable("INVALID_VISUAL_ASSETS", "visual_assets는 object여야 합니다.", null);
        }
        String background = optionalText(visualNode.get("background"), MAX_VISUAL_TEXT_LENGTH, "INVALID_VISUAL_ASSETS");
        return new NarrativeTurn.VisualAssets(
                background,
                readStringArray(visualNode.get("characters"), "INVALID_VISUAL_CHARACTERS"),
                readStringArray(visualNode.get("assets"), "INVALID_VISUAL_ASSETS_LIST")
        );
    }

    private List<String> readStringArray(JsonNode node, String reasonCode) {
        if (node == null || node.isNull()) return List.of();
        if (!node.isArray() || node.size() > MAX_VISUAL_ITEMS) {
            throw recoverable(reasonCode, "visual asset 배열이 올바르지 않습니다.", null);
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || !item.isTextual()) {
                throw recoverable(reasonCode, "visual asset 항목은 문자열이어야 합니다.", null);
            }
            String value = item.asString();
            if (value.length() > MAX_VISUAL_TEXT_LENGTH || containsDisallowedControl(value, true)) {
                throw recoverable(reasonCode, "visual asset 문자열이 허용 범위를 벗어났습니다.", null);
            }
            if (!value.isBlank()) values.add(value);
        }
        return List.copyOf(values);
    }

    private String requiredText(JsonNode parent, String field, int maxLength, boolean allowFormattingControls, String reasonCode) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isTextual()) {
            throw recoverable(reasonCode, "필수 문자열 필드가 누락되었습니다: " + field, null);
        }
        String value = node.asString();
        if (value.isBlank() || value.length() > maxLength || containsDisallowedControl(value, allowFormattingControls)) {
            throw recoverable(reasonCode, "문자열 필드가 허용 범위를 벗어났습니다: " + field, null);
        }
        return value;
    }

    private String optionalText(JsonNode node, int maxLength, String reasonCode) {
        if (node == null || node.isNull()) return "";
        if (!node.isTextual()) {
            throw recoverable(reasonCode, "선택 문자열 필드의 타입이 올바르지 않습니다.", null);
        }
        String value = node.asString();
        if (value.length() > maxLength || containsDisallowedControl(value, true)) {
            throw recoverable(reasonCode, "선택 문자열 필드가 허용 범위를 벗어났습니다.", null);
        }
        return value;
    }

    private boolean containsDisallowedControl(String value, boolean allowFormattingControls) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!Character.isISOControl(ch)) continue;
            if (allowFormattingControls && (ch == '\n' || ch == '\r' || ch == '\t')) continue;
            return true;
        }
        return false;
    }

    private RecoverableNarrativeResponseException recoverable(String reasonCode, String message, Throwable cause) {
        return cause == null
                ? new RecoverableNarrativeResponseException(reasonCode, message)
                : new RecoverableNarrativeResponseException(reasonCode, message, cause);
    }

    private String safeReasonCode(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) return "UNKNOWN";
        return reasonCode.replaceAll("[^A-Z0-9_]", "_");
    }

    private record GeminiApiResponse(List<Candidate> candidates) {}
    private record Candidate(Content content) {}
    private record Content(List<Part> parts) {}
    private record Part(String text) {}

    private record TokenUsage(
            Integer promptTokenCount,
            Integer candidatesTokenCount,
            Integer thoughtsTokenCount,
            Integer totalTokenCount
    ) {
        private static TokenUsage empty() {
            return new TokenUsage(null, null, null, null);
        }
    }
}
