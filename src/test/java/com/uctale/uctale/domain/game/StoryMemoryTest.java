package com.uctale.uctale.domain.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryMemoryTest {

    @Test
    @DisplayName("초기 StoryMemory는 GameState 소유 사실을 중복 저장하지 않는다")
    void initial_DoesNotDuplicateStateOwnedFacts() {
        StoryMemory memory = StoryMemory.initial("세계관", "캐릭터", "오프닝");

        assertThat(memory.canonicalFacts()).isEmpty();
        assertThat(memory.rollingSummary()).isEqualTo(StorySummary.empty());
        assertThat(memory.recentTurns()).containsExactly(GameTurn.opening("오프닝"));
    }

    @Test
    @DisplayName("같은 narrative fact key를 더 최신 source turn으로 갱신하면 이전 fact는 superseded 된다")
    void withCanonicalFact_SupersedesPreviousActiveFact() {
        StoryMemory memory = StoryMemory.initial("세계관", "캐릭터", "오프닝")
                .withCanonicalFact(new CanonicalFact("narrative.promise.guide", "북문에서 만나기로 함", 2))
                .withCanonicalFact(new CanonicalFact("narrative.promise.guide", "약속이 취소됨", 4));

        assertThat(memory.canonicalFacts()).hasSize(2);
        assertThat(memory.canonicalFacts().getFirst().status()).isEqualTo(CanonicalFactStatus.SUPERSEDED);
        assertThat(memory.activeNarrativeFacts())
                .extracting(CanonicalFact::value)
                .containsExactly("약속이 취소됨");
    }

    @Test
    @DisplayName("더 오래되거나 같은 source turn의 fact가 최신 ACTIVE fact를 덮지 못한다")
    void withCanonicalFact_RejectsStaleReplacement() {
        StoryMemory memory = StoryMemory.initial("세계관", "캐릭터", "오프닝")
                .withCanonicalFact(new CanonicalFact("narrative.promise.guide", "최신 약속", 4));

        assertThatThrownBy(() -> memory.withCanonicalFact(
                new CanonicalFact("narrative.promise.guide", "오래된 약속", 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceTurn");
        assertThatThrownBy(() -> memory.withCanonicalFact(
                new CanonicalFact("narrative.promise.guide", "같은 턴 재정의", 4)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceTurn");
    }

    @Test
    @DisplayName("GameState 소유 key는 대소문자 변형도 StoryMemory canonical fact로 저장하지 않는다")
    void withCanonicalFact_RejectsStateOwnedFact() {
        StoryMemory memory = StoryMemory.initial("세계관", "캐릭터", "오프닝");

        assertThatThrownBy(() -> memory.withCanonicalFact(new CanonicalFact("inventory.potion", "3개", 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GameState가 소유");
        assertThatThrownBy(() -> memory.withCanonicalFact(new CanonicalFact("Inventory.Potion", "3개", 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GameState가 소유");
    }

    @Test
    @DisplayName("summary compact는 포함된 turn만 제거하고 metadata를 보존한다")
    void compact_RemovesOnlyCoveredTurns() {
        StoryMemory memory = memoryWithThreeTurns();
        StorySummary summary = new StorySummary(1, 2, 3, "복도를 발견했다.");

        StoryMemory compacted = memory.compact(summary);

        assertThat(compacted.rollingSummary()).isEqualTo(summary);
        assertThat(compacted.recentTurns()).extracting(GameTurn::turnNumber).containsExactly(3);
    }

    @Test
    @DisplayName("recent turn에 존재하지 않는 미래 source 종료 turn으로 summary를 적용하지 않는다")
    void compact_RejectsFutureSourceEnd() {
        StoryMemory memory = memoryWithThreeTurns();
        StorySummary future = new StorySummary(1, 4, 4, "미래 turn까지 포함했다고 주장하는 요약");

        assertThatThrownBy(() -> memory.compact(future))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source 종료 turn");
    }

    private StoryMemory memoryWithThreeTurns() {
        return new StoryMemory(
                List.of(),
                StorySummary.empty(),
                List.of(
                        new GameTurn(1, "", "오프닝"),
                        new GameTurn(2, "문을 연다", "복도를 발견했다"),
                        new GameTurn(3, "복도를 걷는다", "안내인을 만났다")
                )
        );
    }
}
