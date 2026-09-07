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

    public sealed interface StateChange permits TurnAdvanced, ItemAcquired, ItemRemoved,
            ItemQuantityChanged, ItemConsumed, ItemEquipped, ItemUnequipped {
        default String getType() {
            if (this instanceof TurnAdvanced) return "TURN_ADVANCED";
            if (this instanceof ItemAcquired) return "ITEM_ACQUIRED";
            if (this instanceof ItemRemoved) return "ITEM_REMOVED";
            if (this instanceof ItemQuantityChanged) return "ITEM_QUANTITY_CHANGED";
            if (this instanceof ItemConsumed) return "ITEM_CONSUMED";
            if (this instanceof ItemEquipped) return "ITEM_EQUIPPED";
            if (this instanceof ItemUnequipped) return "ITEM_UNEQUIPPED";
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
    private static void validateItemReference(String itemId, String definitionId) {
        if (itemId == null || itemId.isBlank() || definitionId == null || definitionId.isBlank()) throw new IllegalArgumentException("item state change 식별자가 올바르지 않습니다.");
    }
}
