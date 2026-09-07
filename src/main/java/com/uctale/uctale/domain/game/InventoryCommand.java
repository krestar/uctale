package com.uctale.uctale.domain.game;

import java.util.Objects;

public sealed interface InventoryCommand permits
        InventoryCommand.Acquire,
        InventoryCommand.Remove,
        InventoryCommand.ChangeQuantity,
        InventoryCommand.Consume,
        InventoryCommand.Equip,
        InventoryCommand.Unequip {

    record Acquire(OwnedItem item) implements InventoryCommand {
        public Acquire {
            Objects.requireNonNull(item, "acquire item은 필수입니다.");
        }
    }

    record Remove(String itemId) implements InventoryCommand {
        public Remove {
            validateItemId(itemId);
        }
    }

    record ChangeQuantity(String itemId, int newQuantity) implements InventoryCommand {
        public ChangeQuantity {
            validateItemId(itemId);
            if (newQuantity < 1) {
                throw new IllegalArgumentException("newQuantity는 1 이상이어야 합니다.");
            }
        }
    }

    record Consume(String itemId, int quantity) implements InventoryCommand {
        public Consume {
            validateItemId(itemId);
            if (quantity < 1) {
                throw new IllegalArgumentException("consume quantity는 1 이상이어야 합니다.");
            }
        }
    }

    record Equip(String itemId, EquipmentSlot slot) implements InventoryCommand {
        public Equip {
            validateItemId(itemId);
            Objects.requireNonNull(slot, "equipment slot은 필수입니다.");
        }
    }

    record Unequip(EquipmentSlot slot) implements InventoryCommand {
        public Unequip {
            Objects.requireNonNull(slot, "equipment slot은 필수입니다.");
        }
    }

    private static void validateItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("owned item id는 비어 있을 수 없습니다.");
        }
    }
}
