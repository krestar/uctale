package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.PlayerAction;

import java.util.List;
import java.util.Objects;

public record GameResult(
        PlayerAction resolvedAction,
        Outcome outcome,
        SkillCheckResult skillCheckResult,
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
    }

    public GameResult(PlayerAction resolvedAction, Outcome outcome, List<CanonicalFact> canonicalFacts,
                      List<GameEvent> events, List<StateChange> stateChanges, List<String> narrativeCues) {
        this(resolvedAction, outcome, null, canonicalFacts, events, stateChanges, narrativeCues);
    }

    public enum Outcome { RESOLVED }
    public enum GameEvent { ACTION_RESOLVED, SKILL_CHECK_RESOLVED }
    public enum VitalResource { HP, MP }
    public enum VitalsChangeReason { DAMAGE, HEAL, SPEND, RESTORE }
    public enum StatusRemovalReason { EXPLICIT, EXPIRED }

    public sealed interface StateChange permits TurnAdvanced, ItemAcquired, ItemRemoved,
            ItemQuantityChanged, ItemConsumed, ItemEquipped, ItemUnequipped, VitalsChanged,
            StatusEffectApplied, StatusEffectUpdated, StatusDurationChanged, StatusEffectRemoved {
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
            throw new IllegalStateException("지원하지 않는 state change입니다.");
        }
    }

    public record TurnAdvanced(int previousTurn, int nextTurn) implements StateChange {
        public TurnAdvanced {
            if (previousTurn < 1 || nextTurn != previousTurn + 1) {
                throw new IllegalArgumentException("turn state change가 올바르지 않습니다.");
            }
        }
    }

    public record ItemAcquired(OwnedItem item, int resultingQuantity) implements StateChange {
        public ItemAcquired {
            Objects.requireNonNull(item, "acquired item은 필수입니다.");
            if (resultingQuantity < item.quantity()) {
                throw new IllegalArgumentException("획득 후 quantity가 획득 quantity보다 작을 수 없습니다.");
            }
        }
    }

    public record ItemRemoved(OwnedItem item) implements StateChange {
        public ItemRemoved { Objects.requireNonNull(item, "removed item은 필수입니다."); }
    }

    public record ItemQuantityChanged(String itemId, String definitionId, int previousQuantity, int nextQuantity) implements StateChange {
        public ItemQuantityChanged {
            validateItemReference(itemId, definitionId);
            if (previousQuantity < 1 || nextQuantity < 1 || previousQuantity == nextQuantity) {
                throw new IllegalArgumentException("item quantity state change가 올바르지 않습니다.");
            }
        }
    }

    public record ItemConsumed(String itemId, String definitionId, int quantity, int remainingQuantity) implements StateChange {
        public ItemConsumed {
            validateItemReference(itemId, definitionId);
            if (quantity < 1 || remainingQuantity < 0) {
                throw new IllegalArgumentException("item consume state change가 올바르지 않습니다.");
            }
        }
    }

    public record ItemEquipped(EquipmentSlot slot, String itemId, String definitionId) implements StateChange {
        public ItemEquipped {
            Objects.requireNonNull(slot, "equipment slot은 필수입니다.");
            validateItemReference(itemId, definitionId);
        }
    }

    public record ItemUnequipped(EquipmentSlot slot, String itemId, String definitionId) implements StateChange {
        public ItemUnequipped {
            Objects.requireNonNull(slot, "equipment slot은 필수입니다.");
            validateItemReference(itemId, definitionId);
        }
    }

    public record VitalsChanged(
            VitalResource resource,
            int previousValue,
            int nextValue,
            int delta,
            VitalsChangeReason reason
    ) implements StateChange {
        public VitalsChanged {
            Objects.requireNonNull(resource, "vitals resource는 필수입니다.");
            Objects.requireNonNull(reason, "vitals change reason은 필수입니다.");
            if (previousValue < 0 || nextValue < 0 || previousValue == nextValue) {
                throw new IllegalArgumentException("vitals state change 값이 올바르지 않습니다.");
            }
            if ((long) nextValue - previousValue != delta) {
                throw new IllegalArgumentException("vitals delta가 이전/다음 값과 일치하지 않습니다.");
            }
            if (resource == VitalResource.HP && reason != VitalsChangeReason.DAMAGE && reason != VitalsChangeReason.HEAL) {
                throw new IllegalArgumentException("HP에는 DAMAGE/HEAL reason만 사용할 수 있습니다.");
            }
            if (resource == VitalResource.MP && reason != VitalsChangeReason.SPEND && reason != VitalsChangeReason.RESTORE) {
                throw new IllegalArgumentException("MP에는 SPEND/RESTORE reason만 사용할 수 있습니다.");
            }
            boolean decreasing = reason == VitalsChangeReason.DAMAGE || reason == VitalsChangeReason.SPEND;
            if ((decreasing && delta >= 0) || (!decreasing && delta <= 0)) {
                throw new IllegalArgumentException("vitals delta 방향이 reason과 일치하지 않습니다.");
            }
        }
    }

    public record StatusEffectApplied(StatusEffect effect) implements StateChange {
        public StatusEffectApplied { Objects.requireNonNull(effect, "applied status effect는 필수입니다."); }
    }

    public record StatusEffectUpdated(StatusEffect previous, StatusEffect next) implements StateChange {
        public StatusEffectUpdated {
            Objects.requireNonNull(previous, "previous status effect는 필수입니다.");
            Objects.requireNonNull(next, "next status effect는 필수입니다.");
            if (!previous.definitionId().equals(next.definitionId())) {
                throw new IllegalArgumentException("status effect update는 같은 definitionId를 사용해야 합니다.");
            }
            if (previous.expiryTrigger() != next.expiryTrigger()
                    || previous.incapacitating() != next.incapacitating()) {
                throw new IllegalArgumentException("status effect update로 definition metadata를 변경할 수 없습니다.");
            }
            if (previous.equals(next)) {
                throw new IllegalArgumentException("status effect update는 실제 상태를 변경해야 합니다.");
            }
        }
    }

    public record StatusDurationChanged(
            String definitionId,
            int previousRemainingTurns,
            int nextRemainingTurns
    ) implements StateChange {
        public StatusDurationChanged {
            if (definitionId == null || definitionId.isBlank()) {
                throw new IllegalArgumentException("status effect definitionId는 비어 있을 수 없습니다.");
            }
            if (previousRemainingTurns < 2 || nextRemainingTurns != previousRemainingTurns - 1) {
                throw new IllegalArgumentException("status duration state change가 올바르지 않습니다.");
            }
        }
    }

    public record StatusEffectRemoved(StatusEffect effect, StatusRemovalReason reason) implements StateChange {
        public StatusEffectRemoved {
            Objects.requireNonNull(effect, "removed status effect는 필수입니다.");
            Objects.requireNonNull(reason, "status removal reason은 필수입니다.");
            if (reason == StatusRemovalReason.EXPIRED && effect.remainingTurns() != 1) {
                throw new IllegalArgumentException("만료 제거되는 status effect의 remainingTurns는 1이어야 합니다.");
            }
        }
    }

    private static void validateItemReference(String itemId, String definitionId) {
        if (itemId == null || itemId.isBlank() || definitionId == null || definitionId.isBlank()) {
            throw new IllegalArgumentException("item state change 식별자가 올바르지 않습니다.");
        }
    }
}
