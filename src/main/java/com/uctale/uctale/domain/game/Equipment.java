package com.uctale.uctale.domain.game;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record Equipment(Map<EquipmentSlot, String> slots) {

    public Equipment {
        Objects.requireNonNull(slots, "equipment slots는 필수입니다.");
        if (slots.isEmpty()) {
            slots = Map.of();
        } else {
            EnumMap<EquipmentSlot, String> copy = new EnumMap<>(EquipmentSlot.class);
            Set<String> itemIds = new HashSet<>();
            for (Map.Entry<EquipmentSlot, String> entry : slots.entrySet()) {
                EquipmentSlot slot = Objects.requireNonNull(entry.getKey(), "equipment slot은 null일 수 없습니다.");
                String itemId = entry.getValue();
                if (itemId == null || itemId.isBlank()) {
                    throw new IllegalArgumentException("equipped item id는 비어 있을 수 없습니다.");
                }
                if (!itemIds.add(itemId)) {
                    throw new IllegalArgumentException("같은 item을 둘 이상의 slot에 장착할 수 없습니다.");
                }
                copy.put(slot, itemId);
            }
            slots = Collections.unmodifiableMap(copy);
        }
    }

    public static Equipment empty() {
        return new Equipment(Map.of());
    }

    public String itemIdAt(EquipmentSlot slot) {
        Objects.requireNonNull(slot, "equipment slot은 필수입니다.");
        return slots.get(slot);
    }

    public boolean containsItem(String itemId) {
        return slots.containsValue(itemId);
    }

    public Equipment equip(EquipmentSlot slot, String itemId) {
        Objects.requireNonNull(slot, "equipment slot은 필수입니다.");
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("equipped item id는 비어 있을 수 없습니다.");
        }
        if (slots.containsKey(slot)) {
            throw new IllegalStateException("이미 사용 중인 equipment slot입니다: " + slot);
        }
        if (containsItem(itemId)) {
            throw new IllegalStateException("같은 item을 중복 장착할 수 없습니다: " + itemId);
        }
        EnumMap<EquipmentSlot, String> next = new EnumMap<>(EquipmentSlot.class);
        next.putAll(slots);
        next.put(slot, itemId);
        return new Equipment(next);
    }

    public Equipment unequip(EquipmentSlot slot) {
        Objects.requireNonNull(slot, "equipment slot은 필수입니다.");
        if (!slots.containsKey(slot)) {
            throw new IllegalStateException("비어 있는 equipment slot은 해제할 수 없습니다: " + slot);
        }
        EnumMap<EquipmentSlot, String> next = new EnumMap<>(EquipmentSlot.class);
        next.putAll(slots);
        next.remove(slot);
        return new Equipment(next);
    }
}
