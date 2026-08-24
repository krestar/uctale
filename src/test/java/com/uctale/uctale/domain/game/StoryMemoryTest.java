package com.uctale.uctale.domain.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoryMemoryTest {

    @Test
    @DisplayName("최근 턴은 6개만 유지하고 오래된 턴은 rolling summary로 이동한다")
    void append_KeepsSixRecentTurnsAndRollsOlderTurnIntoSummary() {
        GameState state = GameState.initial("세계관", "캐릭터", "1턴");

        for (int turn = 2; turn <= 8; turn++) {
            state = state.advance("행동 " + turn, turn + "턴");
        }

        StoryMemory memory = state.storyMemory();
        assertThat(memory.recentTurns()).hasSize(StoryMemory.RECENT_TURN_LIMIT);
        assertThat(memory.recentTurns()).extracting(GameTurn::turnNumber)
                .containsExactly(3, 4, 5, 6, 7, 8);
        assertThat(memory.rollingSummary()).contains("T1: 1턴");
        assertThat(memory.rollingSummary()).contains("T2: 행동 2 -> 2턴");
        assertThat(memory.canonicalFacts()).hasSize(2);
    }
}
