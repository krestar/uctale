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
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiPromptContractTest {

    @Test
    @DisplayName("progress prompt는 확정 결과/상태/금지 mutation/cues를 분리하고 action token을 노출하지 않는다")
    void buildProgressPrompt_UsesCanonicalContractWithoutActionToken() throws Exception {
        GeminiNarrativeAdapter adapter = new GeminiNarrativeAdapter(new ObjectMapper(), RestClient.builder());
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
                .doesNotContain("SERVER_ONLY_SECRET_TOKEN");
    }

    @Test
    @DisplayName("Skill Check prompt는 서버가 확정한 roll modifier DC total outcome을 그대로 전달한다")
    void buildProgressPrompt_IncludesCanonicalSkillCheckResult() throws Exception {
        GeminiNarrativeAdapter adapter = new GeminiNarrativeAdapter(new ObjectMapper(), RestClient.builder());
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
}
