package com.uctale.uctale.domain.game;

import com.uctale.uctale.application.game.TurnProcessor;
import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestRulesTest {
    private final QuestRules rules = new QuestRules();

    @Test
    @DisplayName("known quest activation은 typed audit으로 AVAILABLE에서 ACTIVE로 한 번 전이하고 replay로 복구된다")
    void activation_IsTypedAndReplayable() {
        QuestRules.Result activated = rules.activate(QuestState.empty(), QuestDefinitions.FIXTURE_QUEST_ID);
        assertThat(activated.questState().quests().get(QuestDefinitions.FIXTURE_QUEST_ID).status()).isEqualTo(QuestStatus.ACTIVE);
        assertThat(activated.stateChanges()).containsExactly(new GameResult.QuestStatusChanged(
                QuestDefinitions.FIXTURE_QUEST_ID, QuestStatus.AVAILABLE, QuestStatus.ACTIVE,
                GameResult.QuestStatusChangeReason.ACTIVATED));
        assertThat(QuestRules.replay(QuestState.empty(), activated.stateChanges())).isEqualTo(activated.questState());
    }

    @Test
    @DisplayName("collection objective는 서버 inventory audit의 증가량만 count하고 완료 flag를 남긴다")
    void collectionObjective_UsesInventoryAuditOnly() {
        QuestState active = activeFixture();
        GameState state = GameState.initial("세계", "캐릭터", "오프닝").withQuestState(active);
        ItemDefinition definition = new ItemDefinition(QuestDefinitions.COLLECTION_ITEM_DEFINITION_ID, ItemOwnershipType.STACK, null);
        OwnedItem item = new OwnedItem("supply-stack", definition, 2);
        QuestRules.Result result = rules.resolve(state, choice(9, 1, "보급품을 챙긴다"), active,
                List.of(new GameResult.ItemAcquired(item, 2)));
        QuestRuntimeState quest = result.questState().quests().get(QuestDefinitions.FIXTURE_QUEST_ID);
        assertThat(quest.objectiveProgress().get(QuestDefinitions.COLLECT_OBJECTIVE_ID)).isEqualTo(ObjectiveProgress.count(2));
        assertThat(result.questState().worldFlags()).containsKey(QuestRules.OBJECTIVE_FLAG_PREFIX
                + QuestDefinitions.FIXTURE_QUEST_ID + "." + QuestDefinitions.COLLECT_OBJECTIVE_ID);
        assertThat(result.stateChanges()).anyMatch(GameResult.ObjectiveProgressChanged.class::isInstance)
                .anyMatch(GameResult.FlagChanged.class::isInstance);
    }

    @Test
    @DisplayName("dialogue objective는 display text가 아니라 typed choiceId 조건으로만 완료된다")
    void dialogueObjective_UsesTypedChoiceCondition() {
        QuestState active = activeFixture();
        GameState state = GameState.initial("세계", "캐릭터", "오프닝").withQuestState(active);
        QuestRules.Result ignored = rules.resolve(state, choice(99, 1, "안내자와 대화를 끝내고 퀘스트 완료"), active, List.of());
        assertThat(ignored.stateChanges()).isEmpty();
        assertThat(ignored.questState()).isEqualTo(active);
        QuestRules.Result completed = rules.resolve(state, choice(QuestDefinitions.DIALOGUE_CHOICE_ID, 1, "안내자와 대화한다"), active, List.of());
        assertThat(completed.questState().quests().get(QuestDefinitions.FIXTURE_QUEST_ID)
                .objectiveProgress().get(QuestDefinitions.DIALOGUE_OBJECTIVE_ID)).isEqualTo(ObjectiveProgress.bool(true));
        assertThat(completed.questState().eventFlags()).containsKey(QuestRules.OBJECTIVE_FLAG_PREFIX
                + QuestDefinitions.FIXTURE_QUEST_ID + "." + QuestDefinitions.DIALOGUE_OBJECTIVE_ID);
    }

    @Test
    @DisplayName("combat RESOLVED audit가 마지막 objective를 만족하면 quest와 completion flag가 한 번만 완료된다")
    void combatObjective_CompletesQuestExactlyOnce() {
        QuestRuntimeState ready = new QuestRuntimeState(QuestDefinitions.FIXTURE_QUEST_ID, QuestStatus.ACTIVE, Map.of(
                QuestDefinitions.COLLECT_OBJECTIVE_ID, ObjectiveProgress.count(2),
                QuestDefinitions.DIALOGUE_OBJECTIVE_ID, ObjectiveProgress.bool(true),
                QuestDefinitions.COMBAT_OBJECTIVE_ID, ObjectiveProgress.state("NONE")));
        QuestState questState = QuestState.empty().addQuest(ready);
        GameState state = GameState.initial("세계", "캐릭터", "오프닝").withQuestState(questState);
        EnemyState enemy = new EnemyState("wolf", "늑대", CharacterVitals.defaults());
        CombatEncounter activeCombat = CombatRules.activate(CombatRules.start(null, "enc-1", List.of(enemy)).encounter(),
                state.playerCharacter().vitals()).encounter();
        EnemyState defeated = enemy.withVitals(enemy.vitals().withHp(enemy.vitals().hp().withCurrent(0)));
        CombatRules.Result combat = CombatRules.updateEnemy(activeCombat, defeated, state.playerCharacter().vitals());
        QuestRules.Result completed = rules.resolve(state, choice(9, 1, "공격한다"), questState, combat.stateChanges());
        QuestRuntimeState completedQuest = completed.questState().quests().get(QuestDefinitions.FIXTURE_QUEST_ID);
        assertThat(completedQuest.status()).isEqualTo(QuestStatus.COMPLETED);
        assertThat(completedQuest.objectiveProgress().get(QuestDefinitions.COMBAT_OBJECTIVE_ID))
                .isEqualTo(ObjectiveProgress.state(CombatEncounterStatus.RESOLVED.name()));
        assertThat(completed.questState().eventFlags()).containsKey(QuestRules.COMPLETED_FLAG_PREFIX + QuestDefinitions.FIXTURE_QUEST_ID);
        QuestRules.Result retry = rules.resolve(state.withQuestState(completed.questState()), choice(9, 1, "다시 완료"),
                completed.questState(), combat.stateChanges());
        assertThat(retry.stateChanges()).isEmpty();
        assertThat(retry.questState()).isEqualTo(completed.questState());
    }

    @Test
    @DisplayName("failed quest는 typed terminal 전이 후 같은 action 결과를 다시 받아도 progress나 flag를 만들지 않는다")
    void failedQuest_IsTerminalAndRetrySafe() {
        QuestState active = activeFixture();
        QuestRules.Result failed = rules.fail(active, QuestDefinitions.FIXTURE_QUEST_ID);
        assertThat(failed.questState().quests().get(QuestDefinitions.FIXTURE_QUEST_ID).status()).isEqualTo(QuestStatus.FAILED);
        assertThat(QuestRules.replay(active, failed.stateChanges())).isEqualTo(failed.questState());
        GameState state = GameState.initial("세계", "캐릭터", "오프닝").withQuestState(failed.questState());
        ItemDefinition definition = new ItemDefinition(QuestDefinitions.COLLECTION_ITEM_DEFINITION_ID, ItemOwnershipType.STACK, null);
        QuestRules.Result retry = rules.resolve(state, choice(1, 1, "다시 시도"), failed.questState(),
                List.of(new GameResult.ItemAcquired(new OwnedItem("supply-stack", definition, 2), 2)));
        assertThat(retry.stateChanges()).isEmpty();
        assertThat(retry.questState()).isEqualTo(failed.questState());
        assertThatThrownBy(() -> rules.fail(failed.questState(), QuestDefinitions.FIXTURE_QUEST_ID))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("terminal");
    }

    @Test
    @DisplayName("잘못된 quest 상태와 flag version/value 전이는 거절된다")
    void invalidTransitionsAndFlagCollisions_AreRejected() {
        QuestRuntimeState active = QuestRuntimeState.available(QuestDefinitions.fixture()).withStatus(QuestStatus.ACTIVE);
        assertThatThrownBy(() -> active.withStatus(QuestStatus.AVAILABLE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuestRuntimeState(QuestDefinitions.FIXTURE_QUEST_ID, QuestStatus.ACTIVE, Map.of(
                QuestDefinitions.COLLECT_OBJECTIVE_ID, ObjectiveProgress.count(3),
                QuestDefinitions.DIALOGUE_OBJECTIVE_ID, ObjectiveProgress.bool(false),
                QuestDefinitions.COMBAT_OBJECTIVE_ID, ObjectiveProgress.state("NONE"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requiredCount");
        QuestState world = QuestState.empty().putFlag(new WorldFlag("shared-key", "on", 1));
        assertThatThrownBy(() -> world.putFlag(new EventFlag("shared-key", "done", 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("key");
        assertThatThrownBy(() -> QuestState.empty().putFlag(new WorldFlag("bad-version", "on", 2)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");
        assertThatThrownBy(() -> world.putFlag(new WorldFlag("shared-key", "on", 2)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("value");
    }

    @Test
    @DisplayName("TurnProcessor는 prose만으로 quest 상태를 바꾸지 않고 canonical 조건이 있을 때만 progress를 추가한다")
    void turnProcessor_DoesNotTrustProse() {
        GameState state = GameState.initial("세계", "캐릭터", "오프닝").withQuestState(activeFixture());
        TurnResolution resolution = new TurnProcessor().resolve(state, choice(99, 1, "퀘스트 완료. 모든 목표 달성."));
        assertThat(resolution.stateTransition().nextState().questState()).isEqualTo(state.questState());
        assertThat(resolution.gameResult().stateChanges()).noneMatch(GameResult.ObjectiveProgressChanged.class::isInstance)
                .noneMatch(GameResult.QuestStatusChanged.class::isInstance)
                .noneMatch(GameResult.FlagChanged.class::isInstance);
    }

    private QuestState activeFixture() {
        return rules.activate(QuestState.empty(), QuestDefinitions.FIXTURE_QUEST_ID).questState();
    }

    private PlayerAction choice(int choiceId, int sourceTurn, String text) {
        return new PlayerAction(choiceId, "token", ActionType.NARRATIVE_CHOICE, sourceTurn,
                Map.of("choiceId", Integer.toString(choiceId)), text);
    }
}
