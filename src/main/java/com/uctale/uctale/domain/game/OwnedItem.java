package com.uctale.uctale.domain.game;

import java.util.Objects;
import java.util.regex.Pattern;

public record OwnedItem(
        String id,
        ItemDefinition definition,
        int quantity
) {
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    public OwnedItem {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("owned item id가 올바르지 않습니다.");
        }
        Objects.requireNonNull(definition, "item definition은 필수입니다.");
        if (quantity < 1) {
            throw new IllegalArgumentException("item quantity는 1 이상이어야 합니다.");
        }
        if (definition.ownershipType() == ItemOwnershipType.INSTANCE && quantity != 1) {
            throw new IllegalArgumentException("INSTANCE item quantity는 1이어야 합니다.");
        }
    }

    public OwnedItem withQuantity(int newQuantity) {
        return new OwnedItem(id, definition, newQuantity);
    }
}
