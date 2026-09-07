package com.uctale.uctale.domain.game;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record Inventory(
        Map<String, OwnedItem> items,
        Equipment equipment
) {

    public Inventory {
        Objects.requireNonNull(items, "inventory items는 필수입니다.");
        Objects.requireNonNull(equipment, "equipment는 필수입니다.");
        if (items.isEmpty()) {
            items = Map.of();
        } else {
            TreeMap<String, OwnedItem> copy = new TreeMap<>();
            Map<String, ItemDefinition> definitionsById = new HashMap<>();
            for (Map.Entry<String, OwnedItem> entry : items.entrySet()) {
                String key = entry.getKey();
                OwnedItem item = Objects.requireNonNull(entry.getValue(), "inventory item은 null일 수 없습니다.");
                if (key == null || !key.equals(item.id())) {
                    throw new IllegalArgumentException("inventory key와 owned item id가 일치해야 합니다.");
                }
                ItemDefinition previousDefinition = definitionsById.putIfAbsent(item.definition().id(), item.definition());
                if (previousDefinition != null && !previousDefinition.equals(item.definition())) {
                    throw new IllegalArgumentException("같은 item definition id는 동일한 definition을 사용해야 합니다.");
                }
                copy.put(key, item);
            }
            items = Collections.unmodifiableMap(copy);
        }
        validateEquipment(items, equipment);
    }

    public static Inventory empty() {
        return new Inventory(Map.of(), Equipment.empty());
    }

    public OwnedItem requireItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("owned item id는 비어 있을 수 없습니다.");
        }
        OwnedItem item = items.get(itemId);
        if (item == null) {
            throw new IllegalArgumentException("존재하지 않는 item입니다: " + itemId);
        }
        return item;
    }

    public Inventory acquire(OwnedItem acquired) {
        Objects.requireNonNull(acquired, "acquired item은 필수입니다.");
        LinkedHashMap<String, OwnedItem> next = new LinkedHashMap<>(items);
        OwnedItem existing = next.get(acquired.id());
        if (existing == null) {
            next.put(acquired.id(), acquired);
            return new Inventory(next, equipment);
        }
        if (existing.definition().ownershipType() != ItemOwnershipType.STACK
                || acquired.definition().ownershipType() != ItemOwnershipType.STACK
                || !existing.definition().equals(acquired.definition())) {
            throw new IllegalArgumentException("같은 owned item id를 다른 instance/definition에 재사용할 수 없습니다.");
        }
        int quantity;
        try {
            quantity = Math.addExact(existing.quantity(), acquired.quantity());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("item quantity가 지원 범위를 벗어났습니다.", exception);
        }
        next.put(existing.id(), existing.withQuantity(quantity));
        return new Inventory(next, equipment);
    }

    public Inventory remove(String itemId) {
        requireNotEquipped(itemId);
        requireItem(itemId);
        LinkedHashMap<String, OwnedItem> next = new LinkedHashMap<>(items);
        next.remove(itemId);
        return new Inventory(next, equipment);
    }

    public Inventory changeQuantity(String itemId, int newQuantity) {
        if (newQuantity < 1) {
            throw new IllegalArgumentException("변경할 item quantity는 1 이상이어야 합니다.");
        }
        OwnedItem item = requireItem(itemId);
        if (item.definition().ownershipType() != ItemOwnershipType.STACK) {
            throw new IllegalArgumentException("INSTANCE item의 quantity는 변경할 수 없습니다.");
        }
        if (item.quantity() == newQuantity) {
            throw new IllegalArgumentException("item quantity는 실제로 변경되어야 합니다.");
        }
        LinkedHashMap<String, OwnedItem> next = new LinkedHashMap<>(items);
        next.put(itemId, item.withQuantity(newQuantity));
        return new Inventory(next, equipment);
    }

    public Inventory consume(String itemId, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("소비 quantity는 1 이상이어야 합니다.");
        }
        OwnedItem item = requireItem(itemId);
        if (quantity > item.quantity()) {
            throw new IllegalArgumentException("보유 quantity보다 많이 소비할 수 없습니다.");
        }
        int remaining = item.quantity() - quantity;
        if (remaining == 0) {
            requireNotEquipped(itemId);
            LinkedHashMap<String, OwnedItem> next = new LinkedHashMap<>(items);
            next.remove(itemId);
            return new Inventory(next, equipment);
        }
        if (item.definition().ownershipType() != ItemOwnershipType.STACK) {
            throw new IllegalArgumentException("INSTANCE item은 부분 소비할 수 없습니다.");
        }
        LinkedHashMap<String, OwnedItem> next = new LinkedHashMap<>(items);
        next.put(itemId, item.withQuantity(remaining));
        return new Inventory(next, equipment);
    }

    public Inventory equip(String itemId, EquipmentSlot slot) {
        OwnedItem item = requireItem(itemId);
        Objects.requireNonNull(slot, "equipment slot은 필수입니다.");
        if (!item.definition().equippable() || item.definition().equipmentSlot() != slot) {
            throw new IllegalArgumentException("item을 요청한 equipment slot에 장착할 수 없습니다.");
        }
        return new Inventory(items, equipment.equip(slot, itemId));
    }

    public Inventory unequip(EquipmentSlot slot) {
        return new Inventory(items, equipment.unequip(slot));
    }

    private void requireNotEquipped(String itemId) {
        if (equipment.containsItem(itemId)) {
            throw new IllegalStateException("장착 중인 item은 제거하거나 모두 소비할 수 없습니다.");
        }
    }

    private static void validateEquipment(Map<String, OwnedItem> items, Equipment equipment) {
        for (Map.Entry<EquipmentSlot, String> entry : equipment.slots().entrySet()) {
            OwnedItem item = items.get(entry.getValue());
            if (item == null) {
                throw new IllegalArgumentException("equipment가 존재하지 않는 item을 참조합니다.");
            }
            if (!item.definition().equippable() || item.definition().equipmentSlot() != entry.getKey()) {
                throw new IllegalArgumentException("equipment slot과 item definition이 일치하지 않습니다.");
            }
        }
    }
}
