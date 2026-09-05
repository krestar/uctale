package com.uctale.uctale.provider.gemini;

import tools.jackson.databind.ObjectMapper;
import com.uctale.uctale.application.narrative.NarrativeContext;
import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.game.ActionResolver;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.SkillCheckResult;
import com.uctale.uctale.domain.game.TurnResolution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiPromptContractTest {

    @Test
    @DisplayName("opening prompt는 한국어 narrative 필드와 영어 visual_assets 언어 계약을 분리한다")
    void openingPrompt_SeparatesNarrativeAndVisualAssetLanguageContracts() {
        GeminiNarrativeAdapter adapter = adapter();

        String prompt = ReflectionTestUtils.invokeMethod(
                adapter,
                "openingPrompt",
                "폐허가 된 서울에서 살아남는 이야기",
                "한국인 정찰자 김하늘"
        );

        assertThat(prompt)
                .contains("[내러티브 출력 언어]")
                .contains("`title`, `story_text`, `choices[].text`")
                .contains("주 언어가 한국어라면 해당 사용자 노출 필드를 모두 한국어로 작성하세요")
                .contains("`visual_assets`만 이미지 provider 계약에 따라 영어로 작성하세요");
    }

    @Test
    @DisplayName("system instruction은 사용자 narrative 언어와 visual_assets 영어 계약을 분리한다")
    void requestBody_SeparatesNarrativeAndVisualAssetLanguageContracts() throws Exception {
        GeminiNarrativeAdapter adapter = adapter();

        String requestBody = adapter.createRequestBody("진행", new GeminiProviderSettings("TEST_API_KEY", "gemini-3.7-flash", "medium", "low").progressThinkingLevel());

        assertThat(requestBody)
                .contains("내러티브 출력 언어 계약")
                .contains("title")
                .contains("story_text")
                .contains("choices[].text")
                .contains("주 언어가 한국어라면")
                .contains("visual_assets")
                .contains("영어(English)");
    }

    @Test
    @DisplayName("progress prompt는 확정 결과/상태/금지 mutation/cues를 분리하고 action token을 노출하지 않는다")
    void buildProgressPrompt_UsesCanonicalContractWithoutActionToken() throws Exception {
        GeminiNarrativeAdapter adapter = adapter();
        GameState state = GameState.initial("폐허 도시", "정찰자", "오프닝");
        PlayerAction action = new PlayerAction(
                7,
                "SERVER_ONLY_SECRET_TOKEN",
                ActionType.NARRATIVE_CHOICE,
                1,
                Map.of("choiceId", "7"),
                "문을 잠근다"
        );
        TurnResolution resolution = new ActionResolver().resolve(state, action);
        NarrativeContext context = NarrativeContext.from("game-result:42:2:100", resolution);

        String prompt = adapter.buildProgressPrompt(context);

        assertThat(prompt)
                .contains("[확정 게임 결과 ID]", "game-result:42:2:100")
                .contains("[확정 게임 결과]", "outcome: RESOLVED")
                .contains("state changes", "previousTurn", "nextTurn")
                .contains("[확정 상태 projection]", "\"turnNumber\":2")
                .contains("[narrative cues]", "문을 잠근다")
                .contains("[금지 canonical mutation]", "HP", "roll")
                .contains("[내러티브 출력 언어]")
                .contains("worldPremise, playerDescription과 누적/최근 narrative context에서 확립된 주 언어")
                .contains("해당 주 언어가 한국어라면 사용자 노출 내러티브 필드를 모두 한국어로 작성하세요")
                .contains("`visual_assets`만 이미지 provider 계약에 따라 영어로 작성")
                .doesNotContain("SERVER_ONLY_SECRET_TOKEN");
    }

    @Test
    @DisplayName("Skill Check prompt는 서버가 확정한 roll modifier DC total outcome을 그대로 전달한다")
    void buildProgressPrompt_IncludesCanonicalSkillCheckResult() throws Exception {
        GeminiNarrativeAdapter adapter = adapter();
        GameState state = GameState.initial("폐허 도시", "정찰자", "오프닝");
        PlayerAction action = new PlayerAction(
                7,
                "SERVER_ONLY_SKILL_TOKEN",
                ActionType.SKILL_CHECK,
                1,
                Map.of(
                        "choiceId", "7",
                        "statType", "WILL",
                        "dc", "10",
                        "situationalModifier", "0"
                ),
                "문을 연다"
        );
        ActionResolver resolver = new ActionResolver();
        SkillCheckResult skillCheck = resolver.rollSkillCheck(state, action, (min, max) -> 10);
        NarrativeContext context = NarrativeContext.from(
                "game-result:42:2:101",
                resolver.resolve(state, action, skillCheck)
        );

        String prompt = adapter.buildProgressPrompt(context);

        assertThat(prompt)
                .contains("skill check:")
                .contains("\"statType\":\"WILL\"")
                .contains("\"rawRoll\":10")
                .contains("\"statModifier\":0")
                .contains("\"situationalModifier\":0")
                .contains("\"dc\":10")
                .contains("\"total\":10")
                .contains("\"outcome\":\"SUCCESS\"")
                .doesNotContain("SERVER_ONLY_SKILL_TOKEN");
    }

    @Test
    @DisplayName("repair instruction은 원 요청 narrative 언어를 유지하고 visual_assets만 영어로 유지한다")
    void recoveryInstruction_PreservesOriginalNarrativeLanguage() {
        GeminiNarrativeAdapter adapter = adapter();

        String instruction = ReflectionTestUtils.invokeMethod(adapter, "recoveryInstruction", "INVALID_CHOICE_ID");

        assertThat(instruction)
                .contains("INVALID_CHOICE_ID")
                .contains("원래 요청에서 확립된 `title`, `story_text`, `choices[].text`의 주 언어를 그대로 유지")
                .contains("번역하거나 다른 언어로 전환하지 마세요")
                .contains("`visual_assets`는 기존과 동일하게 영어로 작성하세요");
    }

    private GeminiNarrativeAdapter adapter() {
        return new GeminiNarrativeAdapter(
                new ObjectMapper(),
                RestClient.builder(),
                new GeminiProviderSettings("TEST_API_KEY", "gemini-3.7-flash", "medium", "low")
        );
    }
}
