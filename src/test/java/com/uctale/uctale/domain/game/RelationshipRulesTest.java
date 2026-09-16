package com.uctale.uctale.domain.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelationshipRulesTest {
    private final RelationshipRules rules = new RelationshipRules();
    private final NpcIdentity guide = new NpcIdentity("guide", "guide-1");

    @Test
    @DisplayName("affinity는 서버 delta로 clamp되고 경계에서 관계 단계가 승급·강등된다")
    void affinity_IsClampedAndDerivesStage() {
        RelationshipRules.Result friendly = rules.apply(RelationshipState.empty(),
                List.of(RelationshipCommand.talk(guide, 20, 1, "greeting")));
        assertThat(friendly.relationshipState().find("guide-1").affinity()).isEqualTo(20);
        assertThat(friendly.relationshipState().find("guide-1").stage()).isEqualTo(RelationshipStage.FRIENDLY);

        RelationshipRules.Result trusted = rules.apply(friendly.relationshipState(),
                List.of(RelationshipCommand.quest(guide, 10_000, 2, "rescue")));
        assertThat(trusted.relationshipState().find("guide-1").affinity()).isEqualTo(100);
        assertThat(trusted.relationshipState().find("guide-1").stage()).isEqualTo(RelationshipStage.TRUSTED);
        assertThat(trusted.relationshipState().find("guide-1").lastAppliedDelta()).isEqualTo(80);

        RelationshipRules.Result hostile = rules.apply(trusted.relationshipState(),
                List.of(RelationshipCommand.gameResult(guide, Integer.MIN_VALUE, 3, "betrayal")));
        assertThat(hostile.relationshipState().find("guide-1").affinity()).isEqualTo(-100);
        assertThat(hostile.relationshipState().find("guide-1").stage()).isEqualTo(RelationshipStage.HOSTILE);
    }

    @Test
    @DisplayName("동일 source turn/key retry는 delta를 중복 적용하지 않고 충돌하는 재사용은 거절한다")
    void retry_IsIdempotentAndSourceKeyCollisionIsRejected() {
        RelationshipCommand command = RelationshipCommand.talk(guide, 15, 7, "thanks");
        RelationshipRules.Result first = rules.apply(RelationshipState.empty(), List.of(command));
        RelationshipRules.Result retry = rules.apply(first.relationshipState(), List.of(command));
        assertThat(retry.relationshipState()).isEqualTo(first.relationshipState());
        assertThat(retry.stateChanges()).isEmpty();
        assertThatThrownBy(() -> rules.apply(first.relationshipState(),
                List.of(RelationshipCommand.talk(guide, 16, 7, "thanks"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("source key");
    }

    @Test
    @DisplayName("같은 turn의 복수 사건 뒤 앞선 사건이 retry되어도 중복 적용되지 않는다")
    void differentEventsInSameTurn_AreNotDroppedOrDuplicated() {
        RelationshipCommand firstCommand = RelationshipCommand.talk(guide, 10, 4, "promise");
        RelationshipCommand secondCommand = RelationshipCommand.quest(guide, 15, 4, "escort");
        RelationshipRules.Result result = rules.apply(RelationshipState.empty(), List.of(firstCommand, secondCommand));
        assertThat(result.relationshipState().find("guide-1").affinity()).isEqualTo(25);
        assertThat(result.stateChanges()).hasSize(2);

        RelationshipRules.Result retriedFirst = rules.apply(result.relationshipState(), List.of(firstCommand));
        assertThat(retriedFirst.relationshipState()).isEqualTo(result.relationshipState());
        assertThat(retriedFirst.stateChanges()).isEmpty();
    }

    @Test
    @DisplayName("clamp로 applied delta가 0이어도 source key를 기록해 충돌 retry를 차단한다")
    void clampedNoOp_StillRecordsDedupeSource() {
        RelationshipState maxed = rules.apply(RelationshipState.empty(),
                List.of(RelationshipCommand.quest(guide, 100, 1, "max"))).relationshipState();
        RelationshipCommand capped = RelationshipCommand.talk(guide, 10, 2, "already-max");
        RelationshipRules.Result first = rules.apply(maxed, List.of(capped));
        assertThat(first.relationshipState().find("guide-1").affinity()).isEqualTo(100);
        assertThat(first.relationshipState().find("guide-1").lastAppliedDelta()).isZero();
        assertThat(rules.apply(first.relationshipState(), List.of(capped)).stateChanges()).isEmpty();
        assertThatThrownBy(() -> rules.apply(first.relationshipState(),
                List.of(RelationshipCommand.talk(guide, -10, 2, "already-max"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("source key");
    }

    @Test
    @DisplayName("instance ID는 definition ID로 검증되고 display name 같은 prose에 의존하지 않는다")
    void identity_IsStableAndIndependentFromDisplayName() {
        RelationshipState state = rules.apply(RelationshipState.empty(),
                List.of(RelationshipCommand.talk(guide, 5, 1, "hello"))).relationshipState();
        assertThat(state.find("guide-1").npc()).isEqualTo(new NpcIdentity("guide", "guide-1"));
        assertThatThrownBy(() -> rules.apply(state, List.of(RelationshipCommand.talk(
                new NpcIdentity("merchant", "guide-1"), 5, 2, "hello-again"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("definitionId");
    }

    @Test
    @DisplayName("stage condition은 quest/action availability에서 canonical relationship만 조회할 수 있다")
    void stageCondition_UsesCanonicalState() {
        RelationshipState relationshipState = rules.apply(RelationshipState.empty(),
                List.of(RelationshipCommand.quest(guide, 50, 1, "saved-village"))).relationshipState();
        GameState state = GameState.initial("세계", "캐릭터", "오프닝").withRelationshipState(relationshipState);
        assertThat(new RelationshipStageCondition("guide-1", RelationshipStage.TRUSTED).matches(state)).isTrue();
        assertThat(new RelationshipStageCondition("unknown", RelationshipStage.NEUTRAL).matches(state)).isFalse();
    }

    @Test
    @DisplayName("NPC narrative memory는 public/private projection을 구조적으로 분리한다")
    void memory_SeparatesPublicAndPrivateFacts() {
        NpcNarrativeMemory memory = NpcNarrativeMemory.empty()
                .withPublicFact("occupation", "guide")
                .withPrivateFact("fear", "dragon");
        NpcRelationship relationship = NpcRelationship.neutral(guide).withMemory(memory);
        assertThat(relationship.narrativeMemory().publicFacts()).isEqualTo(Map.of("occupation", "guide"));
        assertThat(relationship.narrativeMemory().privateFacts()).isEqualTo(Map.of("fear", "dragon"));
        assertThatThrownBy(() -> memory.withPrivateFact("occupation", "spy"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("visibility");
    }

    @Test
    @DisplayName("typed relationship audit은 snapshotless replay로 동일 상태를 복구한다")
    void audit_ReplaysExactly() {
        RelationshipRules.Result first = rules.apply(RelationshipState.empty(),
                List.of(RelationshipCommand.gameResult(guide, -20, 2, "refused-help")));
        assertThat(RelationshipRules.replay(RelationshipState.empty(), first.stateChanges()))
                .isEqualTo(first.relationshipState());
        assertThatThrownBy(() -> RelationshipRules.replay(first.relationshipState(), first.stateChanges()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("previous");
    }
}
