package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class QuestRules {
    public static final String OBJECTIVE_FLAG_PREFIX = "quest.objective.";
    public static final String COMPLETED_FLAG_PREFIX = "quest.completed.";

    public Result activate(QuestState state, String definitionId) {
        Objects.requireNonNull(state, "QuestState는 필수입니다.");
        QuestDefinition definition = QuestDefinitions.find(definitionId)
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 quest definitionId입니다: " + definitionId));
        QuestRuntimeState current = state.quests().get(definitionId);
        QuestState nextState = state;
        if (current == null) {
            current = QuestRuntimeState.available(definition);
            nextState = nextState.addQuest(current);
        }
        if (current.status() != QuestStatus.AVAILABLE) throw new IllegalStateException("AVAILABLE quest만 활성화할 수 있습니다.");
        QuestRuntimeState active = current.withStatus(QuestStatus.ACTIVE);
        nextState = nextState.replaceQuest(active);
        return new Result(nextState, List.of(new GameResult.QuestStatusChanged(
                definitionId, QuestStatus.AVAILABLE, QuestStatus.ACTIVE, GameResult.QuestStatusChangeReason.ACTIVATED)));
    }

    public TurnResolution apply(TurnResolution resolution) {
        Objects.requireNonNull(resolution, "TurnResolution은 필수입니다.");
        GameState previous = resolution.stateTransition().previousState();
        GameState baseNext = resolution.stateTransition().nextState();
        Result result = resolve(previous, resolution.gameResult().resolvedAction(), baseNext.questState(),
                resolution.gameResult().stateChanges());
        if (result.stateChanges().isEmpty()) return resolution;

        GameState next = baseNext.withQuestState(result.questState());
        List<GameResult.StateChange> changes = new ArrayList<>(resolution.gameResult().stateChanges());
        int turnIndex = -1;
        for (int i = 0; i < changes.size(); i++) {
            if (changes.get(i) instanceof GameResult.TurnAdvanced) { turnIndex = i; break; }
        }
        if (turnIndex < 0) throw new IllegalStateException("TurnResolution에 TURN_ADVANCED audit이 없습니다.");
        changes.addAll(turnIndex, result.stateChanges());

        List<GameResult.GameEvent> events = new ArrayList<>(resolution.gameResult().events());
        if (result.stateChanges().stream().anyMatch(GameResult.ObjectiveProgressChanged.class::isInstance)
                || result.stateChanges().stream().anyMatch(GameResult.QuestStatusChanged.class::isInstance)) {
            events.add(GameResult.GameEvent.QUEST_UPDATED);
        }
        if (result.stateChanges().stream().anyMatch(GameResult.FlagChanged.class::isInstance)) {
            events.add(GameResult.GameEvent.FLAG_CHANGED);
        }
        GameResult original = resolution.gameResult();
        GameResult decorated = new GameResult(original.resolvedAction(), original.outcome(), original.skillCheckResult(),
                original.attackResult(), original.canonicalFacts(), events, changes, original.narrativeCues());
        return new TurnResolution(decorated, new StateTransition(previous, next));
    }

    public Result resolve(GameState previousState, PlayerAction action, QuestState baseQuestState,
                          List<GameResult.StateChange> baseChanges) {
        Objects.requireNonNull(previousState, "previousState는 필수입니다.");
        Objects.requireNonNull(action, "action은 필수입니다.");
        Objects.requireNonNull(baseQuestState, "baseQuestState는 필수입니다.");
        List<GameResult.StateChange> changes = baseChanges == null ? List.of() : List.copyOf(baseChanges);
        QuestState current = baseQuestState;
        List<GameResult.StateChange> questChanges = new ArrayList<>();

        for (QuestRuntimeState quest : baseQuestState.quests().values()) {
            if (quest.status() != QuestStatus.ACTIVE) continue;
            QuestDefinition definition = QuestDefinitions.find(quest.definitionId()).orElseThrow();
            QuestRuntimeState updated = quest;
            for (ObjectiveDefinition objective : definition.objectives().values()) {
                ObjectiveProgress before = updated.objectiveProgress().get(objective.objectiveId());
                if (objective.completed(before)) continue;
                ObjectiveProgress after = evaluate(objective, before, action, changes);
                if (after.equals(before)) continue;
                updated = updated.withProgress(objective.objectiveId(), after);
                questChanges.add(new GameResult.ObjectiveProgressChanged(
                        quest.definitionId(), objective.objectiveId(), before, after, objective.trigger()));
                if (objective.completed(after)) {
                    GameFlag flag = objective.trigger() == ObjectiveTrigger.COLLECTION
                            ? new WorldFlag(OBJECTIVE_FLAG_PREFIX + quest.definitionId() + "." + objective.objectiveId(), "completed", 1)
                            : new EventFlag(OBJECTIVE_FLAG_PREFIX + quest.definitionId() + "." + objective.objectiveId(), "completed", 1);
                    current = putNewFlag(current, flag, GameResult.FlagChangeReason.OBJECTIVE_COMPLETED, questChanges);
                }
            }
            if (updated.objectivesCompleted()) {
                QuestStatus previousStatus = updated.status();
                updated = updated.withStatus(QuestStatus.COMPLETED);
                questChanges.add(new GameResult.QuestStatusChanged(quest.definitionId(), previousStatus,
                        QuestStatus.COMPLETED, GameResult.QuestStatusChangeReason.OBJECTIVES_COMPLETED));
                EventFlag completed = new EventFlag(COMPLETED_FLAG_PREFIX + quest.definitionId(), "completed", 1);
                current = putNewFlag(current, completed, GameResult.FlagChangeReason.QUEST_COMPLETED, questChanges);
            }
            if (!updated.equals(quest)) current = current.replaceQuest(updated);
        }
        return new Result(current, List.copyOf(questChanges));
    }

    private ObjectiveProgress evaluate(ObjectiveDefinition objective, ObjectiveProgress progress,
                                       PlayerAction action, List<GameResult.StateChange> changes) {
        return switch (objective.trigger()) {
            case COLLECTION -> collectionProgress(objective, progress, changes);
            case DIALOGUE -> dialogueProgress(objective, progress, action);
            case COMBAT -> combatProgress(objective, progress, changes);
        };
    }

    private ObjectiveProgress collectionProgress(ObjectiveDefinition objective, ObjectiveProgress progress,
                                                 List<GameResult.StateChange> changes) {
        long increment = 0;
        for (GameResult.StateChange change : changes) {
            if (change instanceof GameResult.ItemAcquired acquired
                    && acquired.item().definition().id().equals(objective.targetKey())) {
                increment += acquired.item().quantity();
            } else if (change instanceof GameResult.ItemQuantityChanged quantity
                    && quantity.definitionId().equals(objective.targetKey())
                    && quantity.nextQuantity() > quantity.previousQuantity()) {
                increment += (long) quantity.nextQuantity() - quantity.previousQuantity();
            }
        }
        if (increment == 0) return progress;
        long next = (long) progress.count() + increment;
        return ObjectiveProgress.count((int) Math.min(next, objective.requiredCount()));
    }

    private ObjectiveProgress dialogueProgress(ObjectiveDefinition objective, ObjectiveProgress progress,
                                               PlayerAction action) {
        if (action.type() != ActionType.NARRATIVE_CHOICE) return progress;
        String actual = action.arguments().get(objective.targetKey());
        return objective.targetValue().equals(actual) ? ObjectiveProgress.bool(true) : progress;
    }

    private ObjectiveProgress combatProgress(ObjectiveDefinition objective, ObjectiveProgress progress,
                                             List<GameResult.StateChange> changes) {
        for (GameResult.StateChange change : changes) {
            if (change instanceof GameResult.CombatEncounterChanged combat
                    && combat.nextEncounter().status().name().equals(objective.targetValue())) {
                return ObjectiveProgress.state(objective.targetValue());
            }
        }
        return progress;
    }

    private QuestState putNewFlag(QuestState state, GameFlag flag, GameResult.FlagChangeReason reason,
                                  List<GameResult.StateChange> changes) {
        GameFlag existing = state.flag(flag.namespace(), flag.key());
        if (existing != null) {
            if (!existing.equals(flag)) throw new IllegalStateException("quest rule이 기존 flag와 충돌합니다: " + flag.key());
            return state;
        }
        QuestState next = state.putFlag(flag);
        changes.add(new GameResult.FlagChanged(flag.namespace(), flag.key(), null, flag, reason));
        return next;
    }

    public static QuestState replay(QuestState previous, List<GameResult.StateChange> changes) {
        Objects.requireNonNull(previous, "previous QuestState는 필수입니다.");
        QuestState state = previous;
        if (changes == null) return state;
        for (GameResult.StateChange change : changes) {
            if (change instanceof GameResult.ObjectiveProgressChanged progress) {
                QuestRuntimeState quest = requireQuest(state, progress.questDefinitionId());
                ObjectiveProgress actual = quest.objectiveProgress().get(progress.objectiveId());
                if (!Objects.equals(actual, progress.previousProgress())) throw new IllegalArgumentException("objective audit previous progress가 canonical state와 일치하지 않습니다.");
                state = state.replaceQuest(quest.withProgress(progress.objectiveId(), progress.nextProgress()));
            } else if (change instanceof GameResult.QuestStatusChanged status) {
                QuestRuntimeState quest = state.quests().get(status.questDefinitionId());
                if (quest == null && status.reason() == GameResult.QuestStatusChangeReason.ACTIVATED
                        && status.previousStatus() == QuestStatus.AVAILABLE) {
                    QuestDefinition definition = QuestDefinitions.find(status.questDefinitionId())
                            .orElseThrow(() -> new IllegalArgumentException("quest audit가 알 수 없는 definition을 참조합니다: " + status.questDefinitionId()));
                    state = state.addQuest(QuestRuntimeState.available(definition));
                    quest = state.quests().get(status.questDefinitionId());
                }
                if (quest == null) throw new IllegalArgumentException("quest audit가 존재하지 않는 quest를 참조합니다: " + status.questDefinitionId());
                if (quest.status() != status.previousStatus()) throw new IllegalArgumentException("quest status audit previous 값이 canonical state와 일치하지 않습니다.");
                state = state.replaceQuest(quest.withStatus(status.nextStatus()));
            } else if (change instanceof GameResult.FlagChanged flag) {
                GameFlag actual = state.flag(flag.namespace(), flag.key());
                if (!Objects.equals(actual, flag.previousValue())) throw new IllegalArgumentException("flag audit previous 값이 canonical state와 일치하지 않습니다.");
                state = state.putFlag(flag.nextValue());
            }
        }
        return state;
    }

    private static QuestRuntimeState requireQuest(QuestState state, String definitionId) {
        QuestRuntimeState quest = state.quests().get(definitionId);
        if (quest == null) throw new IllegalArgumentException("quest audit가 존재하지 않는 quest를 참조합니다: " + definitionId);
        return quest;
    }

    public record Result(QuestState questState, List<GameResult.StateChange> stateChanges) {
        public Result {
            Objects.requireNonNull(questState, "questState는 필수입니다.");
            stateChanges = stateChanges == null ? List.of() : List.copyOf(stateChanges);
        }
    }
}
