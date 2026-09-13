package com.uctale.uctale.domain.game;

import java.util.Objects;
import java.util.regex.Pattern;

public record ItemDefinition(
        String id,
        ItemOwnershipType ownershipType,
        EquipmentSlot equipmentSlot,
        ItemCombatModifiers combatModifiers
) {
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    public ItemDefinition {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("item definition id가 올바르지 않습니다.");
        }
        Objects.requireNonNull(ownershipType, "item ownershipType은 필수입니다.");
        Objects.requireNonNull(combatModifiers, "item combatModifiers는 필수입니다.");
        if (equipmentSlot != null && ownershipType != ItemOwnershipType.INSTANCE) {
            throw new IllegalArgumentException("장착 가능한 item은 INSTANCE ownership이어야 합니다.");
        }
        if (equipmentSlot == null && !combatModifiers.neutral()) {
            throw new IllegalArgumentException("장착할 수 없는 item에는 combat modifier를 부여할 수 없습니다.");
        }
    }

    public ItemDefinition(String id, ItemOwnershipType ownershipType, EquipmentSlot equipmentSlot) {
        this(id, ownershipType, equipmentSlot, ItemCombatModifiers.none());
    }

    public boolean equippable() {
        return equipmentSlot != null;
    }
}
