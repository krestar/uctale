package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.PlayerAction;

import java.util.List;
import java.util.Objects;

public record GameResult(
        PlayerAction resolvedAction,
        Outcome outcome,
        SkillCheckResult skillCheckResult,
        AttackResult attackResult,
        List<CanonicalFact> canonicalFacts,
        List<GameEvent> events,
        List<StateChange> stateChanges,
        List<String> narrativeCues
) {
    public GameResult {
        Objects.requireNonNull(resolvedAction, "resolvedAction은 필수입니다.");
        Objects.requireNonNull(outcome, "outcome은 필수입니다.");
        canonicalFacts = canonicalFacts == null ? List.of() : List.copyOf(canonicalFacts);
        events = events == null ? List.of() : List.copyOf(events);
        stateChanges = stateChanges == null ? List.of() : List.copyOf(stateChanges);
        narrativeCues = narrativeCues == null ? List.of() : List.copyOf(narrativeCues);
        if (skillCheckResult != null && attackResult != null) throw new IllegalArgumentException("한 GameResult에 Skill Check와 Attack 판정을 동시에 기록할 수 없습니다.");
    }

    public GameResult(PlayerAction resolvedAction, Outcome outcome, SkillCheckResult skillCheckResult,
                      List<CanonicalFact> canonicalFacts, List<GameEvent> events,
                      List<StateChange> stateChanges, List<String> narrativeCues) {
        this(resolvedAction, outcome, skillCheckResult, null, canonicalFacts, events, stateChanges, narrativeCues);
    }

    public GameResult(PlayerAction resolvedAction, Outcome outcome, List<CanonicalFact> canonicalFacts,
                      List<GameEvent> events, List<StateChange> stateChanges, List<String> narrativeCues) {
        this(resolvedAction, outcome, null, null, canonicalFacts, events, stateChanges, narrativeCues);
    }

    public enum Outcome { RESOLVED }
    public enum GameEvent {
        ACTION_RESOLVED,
        SKILL_CHECK_RESOLVED,
        COMBAT_ACTION_RESOLVED,
        ATTACK_RESOLVED,
        ABILITY_RESOLVED,
        COMBAT_ENCOUNTER_CHANGED,
        QUEST_UPDATED,
        FLAG_CHANGED
    }
    public enum VitalResource { HP, MP }
    public enum VitalsChangeReason { DAMAGE, HEAL, SPEND, RESTORE }
    public enum StatusRemovalReason { EXPLICIT, EXPIRED }
    public enum AbilityCooldownChangeReason { USED, TURN_ENDED }
    public enum QuestStatusChangeReason { ACTIVATED, OBJECTIVES_COMPLETED, FAILED }
    public enum FlagChangeReason { OBJECTIVE_COMPLETED, QUEST_COMPLETED }

    public sealed interface StateChange permits TurnAdvanced, ItemAcquired, ItemRemoved,
            ItemQuantityChanged, ItemConsumed, ItemEquipped, ItemUnequipped, VitalsChanged,
            StatusEffectApplied, StatusEffectUpdated, StatusDurationChanged, StatusEffectRemoved,
            AttackResolved, AbilityResolved, AbilityCooldownChanged, CombatEncounterChanged,
            ObjectiveProgressChanged, QuestStatusChanged, FlagChanged {
        default String getType() {
            if (this instanceof TurnAdvanced) return "TURN_ADVANCED";
            if (this instanceof ItemAcquired) return "ITEM_ACQUIRED";
            if (this instanceof ItemRemoved) return "ITEM_REMOVED";
            if (this instanceof ItemQuantityChanged) return "ITEM_QUANTITY_CHANGED";
            if (this instanceof ItemConsumed) return "ITEM_CONSUMED";
            if (this instanceof ItemEquipped) return "ITEM_EQUIPPED";
            if (this instanceof ItemUnequipped) return "ITEM_UNEQUIPPED";
            if (this instanceof VitalsChanged) return "VITALS_CHANGED";
            if (this instanceof StatusEffectApplied) return "STATUS_EFFECT_APPLIED";
            if (this instanceof StatusEffectUpdated) return "STATUS_EFFECT_UPDATED";
            if (this instanceof StatusDurationChanged) return "STATUS_DURATION_CHANGED";
            if (this instanceof StatusEffectRemoved) return "STATUS_EFFECT_REMOVED";
            if (this instanceof AttackResolved) return "ATTACK_RESOLVED";
            if (this instanceof AbilityResolved) return "ABILITY_RESOLVED";
            if (this instanceof AbilityCooldownChanged) return "ABILITY_COOLDOWN_CHANGED";
            if (this instanceof CombatEncounterChanged) return "COMBAT_ENCOUNTER_CHANGED";
            if (this instanceof ObjectiveProgressChanged) return "OBJECTIVE_PROGRESS_CHANGED";
            if (this instanceof QuestStatusChanged) return "QUEST_STATUS_CHANGED";
            if (this instanceof FlagChanged) return "FLAG_CHANGED";
            throw new IllegalStateException("지원하지 않는 state change입니다.");
        }
    }

    public record TurnAdvanced(int previousTurn, int nextTurn) implements StateChange {
        public TurnAdvanced {
            if (previousTurn < 1 || nextTurn != previousTurn + 1) throw new IllegalArgumentException("turn state change가 올바르지 않습니다.");
        }
    }

    public record ItemAcquired(OwnedItem item, int resultingQuantity) implements StateChange {
        public ItemAcquired {
            Objects.requireNonNull(item, "acquired item은 필수입니다.");
            if (resultingQuantity < item.quantity()) throw new IllegalArgumentException("획득 후 quantity가 획득 quantity보다 작을 수 없습니다.");
        }
    }

    public record ItemRemoved(OwnedItem item) implements StateChange {
        public ItemRemoved { Objects.requireNonNull(item, "removed item은 필수입니다."); }
    }

    public record ItemQuantityChanged(String itemId, String definitionId, int previousQuantity, int nextQuantity) implements StateChange {
        public ItemQuantityChanged {
            validateItemReference(itemId, definitionId);
            if (previousQuantity < 1 || nextQuantity < 1 || previousQuantity == nextQuantity) throw new IllegalArgumentException("item quantity state change가 올바르지 않습니다.");
        }
    }

    public record ItemConsumed(String itemId, String definitionId, int quantity, int remainingQuantity) implements StateChange {
        public ItemConsumed {
            validateItemReference(itemId, definitionId);
            if (quantity < 1 || remainingQuantity < 0) throw new IllegalArgumentException("item consume state change가 올바르지 않습니다.");
        }
    }

    public record ItemEquipped(EquipmentSlot slot, String itemId, String definitionId) implements StateChange {
        public ItemEquipped { Objects.requireNonNull(slot, "equipment slot은 필수입니다."); validateItemReference(itemId, definitionId); }
    }

    public record ItemUnequipped(EquipmentSlot slot, String itemId, String definitionId) implements StateChange {
        public ItemUnequipped { Objects.requireNonNull(slot, "equipment slot은 필수입니다."); validateItemReference(itemId, definitionId); }
    }

    public record VitalsChanged(VitalResource resource, int previousValue, int nextValue, int delta, VitalsChangeReason reason) implements StateChange {
        public VitalsChanged {
            Objects.requireNonNull(resource, "vitals resource는 필수입니다.");
            Objects.requireNonNull(reason, "vitals change reason은 필수입니다.");
            if (previousValue < 0 || nextValue < 0 || previousValue == nextValue) throw new IllegalArgumentException("vitals state change 값이 올바르지 않습니다.");
            if ((long) nextValue - previousValue != delta) throw new IllegalArgumentException("vitals delta가 이전/다음 값과 일치하지 않습니다.");
            if (resource == VitalResource.HP && reason != VitalsChangeReason.DAMAGE && reason != VitalsChangeReason.HEAL) throw new IllegalArgumentException("HP에는 DAMAGE/HEAL reason만 사용할 수 있습니다.");
            if (resource == VitalResource.MP && reason != VitalsChangeReason.SPEND && reason != VitalsChangeReason.RESTORE) throw new IllegalArgumentException("MP에는 SPEND/RESTORE reason만 사용할 수 있습니다.");
            boolean decreasing = reason == VitalsChangeReason.DAMAGE || reason == VitalsChangeReason.SPEND;
            if ((decreasing && delta >= 0) || (!decreasing && delta <= 0)) throw new IllegalArgumentException("vitals delta 방향이 reason과 일치하지 않습니다.");
        }
    }

    public record StatusEffectApplied(StatusEffect effect) implements StateChange {
        public StatusEffectApplied { Objects.requireNonNull(effect, "applied status effect는 필수입니다."); }
    }

    public record StatusEffectUpdated(StatusEffect previous, StatusEffect next) implements StateChange {
        public StatusEffectUpdated {
            Objects.requireNonNull(previous, "previous status effect는 필수입니다.");
            Objects.requireNonNull(next, "next status effect는 필수입니다.");
            if (!previous.definitionId().equals(next.definitionId())) throw new IllegalArgumentException("status effect update는 같은 definitionId를 사용해야 합니다.");
            if (previous.expiryTrigger() != next.expiryTrigger() || previous.incapacitating() != next.incapacitating()) throw new IllegalArgumentException("status effect update로 definition metadata를 변경할 수 없습니다.");
            if (previous.equals(next)) throw new IllegalArgumentException("status effect update는 실제 상태를 변경해야 합니다.");
        }
    }

    public record StatusDurationChanged(String definitionId, int previousRemainingTurns, int nextRemainingTurns) implements StateChange {
        public StatusDurationChanged {
            if (definitionId == null || definitionId.isBlank()) throw new IllegalArgumentException("status effect definitionId는 비어 있을 수 없습니다.");
            if (previousRemainingTurns < 2 || nextRemainingTurns != previousRemainingTurns - 1) throw new IllegalArgumentException("status duration state change가 올바르지 않습니다.");
        }
    }

    public record StatusEffectRemoved(StatusEffect effect, StatusRemovalReason reason) implements StateChange {
        public StatusEffectRemoved {
            Objects.requireNonNull(effect, "removed status effect는 필수입니다.");
            Objects.requireNonNull(reason, "status removal reason은 필수입니다.");
            if (reason == StatusRemovalReason.EXPIRED && effect.remainingTurns() != 1) throw new IllegalArgumentException("만료 제거되는 status effect의 remainingTurns는 1이어야 합니다.");
        }
    }

    public record AttackResolved(AttackResult result) implements StateChange {
        public AttackResolved { Objects.requireNonNull(result, "attack result는 필수입니다."); }
    }

    public record AbilityResolved(AbilityResult result) implements StateChange {
        public AbilityResolved { Objects.requireNonNull(result, "ability result는 필수입니다."); }
    }

    public record AbilityCooldownChanged(String definitionId, int previousRemainingTurns, int nextRemainingTurns,
                                         AbilityCooldownChangeReason reason) implements StateChange {
        public AbilityCooldownChanged {
            if (definitionId == null || definitionId.isBlank()) throw new IllegalArgumentException("ability definitionId는 비어 있을 수 없습니다.");
            Objects.requireNonNull(reason, "ability cooldown reason은 필수입니다.");
            if (previousRemainingTurns < 0 || nextRemainingTurns < 0 || previousRemainingTurns == nextRemainingTurns) throw new IllegalArgumentException("ability cooldown state change 값이 올바르지 않습니다.");
            if (reason == AbilityCooldownChangeReason.USED) {
                if (previousRemainingTurns != 0 || nextRemainingTurns < 1) throw new IllegalArgumentException("USED cooldown change가 올바르지 않습니다.");
            } else if (previousRemainingTurns < 1 || nextRemainingTurns != previousRemainingTurns - 1) {
                throw new IllegalArgumentException("TURN_ENDED cooldown change가 올바르지 않습니다.");
            }
        }
    }

    public record CombatEncounterChanged(CombatEncounter previousEncounter, CombatEncounter nextEncounter,
                                         CombatChangeReason reason) implements StateChange {
        public CombatEncounterChanged {
            CombatRules.validateChange(previousEncounter, nextEncounter, reason);
            validateCombatDelta(previousEncounter, nextEncounter, reason);
        }
    }

    public record ObjectiveProgressChanged(String questDefinitionId, String objectiveId,
                                           ObjectiveProgress previousProgress, ObjectiveProgress nextProgress,
                                           ObjectiveTrigger reason) implements StateChange {
        public ObjectiveProgressChanged {
            validateQuestReference(questDefinitionId, objectiveId);
            Objects.requireNonNull(previousProgress, "previous objective progress는 필수입니다.");
            Objects.requireNonNull(nextProgress, "next objective progress는 필수입니다.");
            Objects.requireNonNull(reason, "objective progress reason은 필수입니다.");
            if (previousProgress.type() != nextProgress.type() || previousProgress.equals(nextProgress)) throw new IllegalArgumentException("objective progress change가 올바르지 않습니다.");
        }
    }

    public record QuestStatusChanged(String questDefinitionId, QuestStatus previousStatus, QuestStatus nextStatus,
                                     QuestStatusChangeReason reason) implements StateChange {
        public QuestStatusChanged {
            if (questDefinitionId == null || questDefinitionId.isBlank()) throw new IllegalArgumentException("quest definitionId는 비어 있을 수 없습니다.");
            Objects.requireNonNull(previousStatus, "previous quest status는 필수입니다.");
            Objects.requireNonNull(nextStatus, "next quest status는 필수입니다.");
            Objects.requireNonNull(reason, "quest status reason은 필수입니다.");
            QuestRuntimeState.validateStatusTransition(previousStatus, nextStatus);
            boolean reasonValid = switch (reason) {
                case ACTIVATED -> previousStatus == QuestStatus.AVAILABLE && nextStatus == QuestStatus.ACTIVE;
                case OBJECTIVES_COMPLETED -> previousStatus == QuestStatus.ACTIVE && nextStatus == QuestStatus.COMPLETED;
                case FAILED -> nextStatus == QuestStatus.FAILED;
            };
            if (!reasonValid) throw new IllegalArgumentException("quest status reason이 transition과 일치하지 않습니다.");
        }
    }

    public record FlagChanged(FlagNamespace namespace, String key, GameFlag previousValue, GameFlag nextValue,
                              FlagChangeReason reason) implements StateChange {
        public FlagChanged {
            Objects.requireNonNull(namespace, "flag namespace는 필수입니다.");
            if (key == null || key.isBlank()) throw new IllegalArgumentException("flag key는 비어 있을 수 없습니다.");
            Objects.requireNonNull(nextValue, "next flag value는 필수입니다.");
            Objects.requireNonNull(reason, "flag change reason은 필수입니다.");
            if (!key.equals(nextValue.key()) || nextValue.namespace() != namespace) throw new IllegalArgumentException("next flag key/namespace가 audit과 일치하지 않습니다.");
            if (previousValue == null) {
                if (nextValue.version() != 1) throw new IllegalArgumentException("새 flag version은 1이어야 합니다.");
            } else {
                if (!key.equals(previousValue.key()) || previousValue.namespace() != namespace) throw new IllegalArgumentException("previous flag key/namespace가 audit과 일치하지 않습니다.");
                if (nextValue.version() != previousValue.version() + 1 || previousValue.value().equals(nextValue.value())) throw new IllegalArgumentException("flag version/value transition이 올바르지 않습니다.");
            }
        }
    }

    private static void validateCombatDelta(CombatEncounter previous, CombatEncounter next, CombatChangeReason reason) {
        if (previous == null) return;
        switch (reason) {
            case PARTICIPANT_JOINED -> {
                if (next.enemies().size() != previous.enemies().size() + 1 || !next.enemies().entrySet().containsAll(previous.enemies().entrySet())) throw new IllegalArgumentException("PARTICIPANT_JOINED는 정확히 한 enemy만 추가해야 합니다.");
            }
            case PARTICIPANT_LEFT -> {
                if (next.enemies().size() != previous.enemies().size() - 1 || !previous.enemies().entrySet().containsAll(next.enemies().entrySet())) throw new IllegalArgumentException("PARTICIPANT_LEFT는 정확히 한 enemy만 제거해야 합니다.");
            }
            case ACTIVATED, ACTOR_ADVANCED, RESOLVED, ESCAPED -> {
                if (!previous.enemies().equals(next.enemies())) throw new IllegalArgumentException(reason + " change로 enemy 상태나 참가자를 변경할 수 없습니다.");
            }
            case ENEMY_UPDATED -> {
                if (!previous.enemies().keySet().equals(next.enemies().keySet())) throw new IllegalArgumentException("ENEMY_UPDATED로 참가자 집합을 변경할 수 없습니다.");
                long changed = previous.enemies().keySet().stream().filter(id -> !previous.enemies().get(id).equals(next.enemies().get(id))).count();
                if (changed != 1) throw new IllegalArgumentException("ENEMY_UPDATED는 정확히 한 enemy 상태만 변경해야 합니다.");
            }
            case STARTED -> { }
        }
    }

    private static void validateItemReference(String itemId, String definitionId) {
        if (itemId == null || itemId.isBlank() || definitionId == null || definitionId.isBlank()) throw new IllegalArgumentException("item state change 식별자가 올바르지 않습니다.");
    }

    private static void validateQuestReference(String questDefinitionId, String objectiveId) {
        if (questDefinitionId == null || questDefinitionId.isBlank() || objectiveId == null || objectiveId.isBlank()) throw new IllegalArgumentException("quest/objective 식별자가 올바르지 않습니다.");
    }
}
