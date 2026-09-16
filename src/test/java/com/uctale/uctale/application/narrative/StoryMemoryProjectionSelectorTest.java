package com.uctale.uctale.application.narrative;

import com.uctale.uctale.domain.game.CanonicalFact;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.StoryMemory;
import com.uctale.uctale.domain.game.StorySummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoryMemoryProjectionSelectorTest {

    @Test
    @DisplayName("장기 세션 memory projection은 명시된 token budget을 넘지 않고 최신 turn을 포함한다")
    void longSession_StaysWithinTokenBudget() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        String longAction = "행동".repeat(500);
        String longStory = "장면".repeat(2_000);
        for (int turn = 2; turn <= 30; turn++) {
            state = state.advance(longAction + turn, longStory + turn);
        }

        StoryMemoryProjectionSelector.Projection projection = StoryMemoryProjectionSelector.project(state);

        assertThat(projection.estimatedTokens()).isLessThanOrEqualTo(StoryMemoryProjectionSelector.MEMORY_TOKEN_BUDGET);
        assertThat(projection.recentTurns()).isNotEmpty();
        assertThat(projection.recentTurns().getLast().turnNumber()).isEqualTo(30);
        assertThat(StoryMemoryProjectionSelector.estimateRawTurns(state.storyMemory().recentTurns()))
                .isGreaterThan(StoryMemoryProjectionSelector.SUMMARY_TRIGGER_TOKEN_BUDGET);
    }

    @Test
    @DisplayName("superseded fact는 prompt projection에서 제외하고 최신 active fact만 전달한다")
    void supersededFact_IsExcludedFromProjection() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        StoryMemory memory = state.storyMemory()
                .withCanonicalFact(new CanonicalFact("narrative.promise.guide", "북문에서 만나기로 함", 1))
                .withCanonicalFact(new CanonicalFact("narrative.promise.guide", "약속이 취소됨", 2));
        state = state.withStoryMemory(memory);

        StoryMemoryProjectionSelector.Projection projection = StoryMemoryProjectionSelector.project(state);

        assertThat(projection.canonicalFacts())
                .extracting(CanonicalFact::value)
                .containsExactly("약속이 취소됨");
    }

    @Test
    @DisplayName("현재 state version보다 미래인 summary는 drift로 간주해 projection에서 제외한다")
    void futureSummary_IsExcluded() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        StoryMemory memory = new StoryMemory(
                state.storyMemory().canonicalFacts(),
                new StorySummary(1, 1, 2, "미래 상태를 전제로 한 잘못된 요약"),
                state.storyMemory().recentTurns()
        );

        StoryMemoryProjectionSelector.Projection projection = StoryMemoryProjectionSelector.project(state.withStoryMemory(memory));

        assertThat(projection.rollingSummary()).isEqualTo(StorySummary.empty());
    }
}
