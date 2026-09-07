package com.uctale.uctale.domain.game;

import java.util.Objects;
import java.util.regex.Pattern;

public record ItemDefinition(
        String id,
        ItemOwnershipType ownershipType,
        EquipmentSlot equipmentSlot
) {
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    public ItemDefinition {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("item definition id가 올바르지 않습니다.");
        }
        Objects.requireNonNull(ownershipType, "item ownershipType은 필수입니다.");
        if (equipmentSlot != null && ownershipType != ItemOwnershipType.INSTANCE) {
            throw new IllegalArgumentException("장착 가능한 item은 INSTANCE ownership이어야 합니다.");
        }
    }

    public boolean equippable() {
        return equipmentSlot != null;
    }
}
