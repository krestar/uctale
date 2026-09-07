package com.uctale.uctale.domain.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InventoryRules {

    public Result apply(Inventory inventory, List<InventoryCommand> commands) {
        Objects.requireNonNull(inventory, "inventory는 필수입니다.");
        if (commands == null || commands.isEmpty()) {
            return new Result(inventory, List.of());
        }

        Inventory current = inventory;
        List<GameResult.StateChange> changes = new ArrayList<>();
        for (InventoryCommand command : commands) {
            Objects.requireNonNull(command, "inventory command는 null일 수 없습니다.");
            Applied applied = applyOne(current, command);
            current = applied.inventory();
            changes.add(applied.stateChange());
        }
        return new Result(current, changes);
    }

    public static Inventory replay(Inventory inventory, List<GameResult.StateChange> stateChanges) {
        Objects.requireNonNull(inventory, "inventory는 필수입니다.");
        if (stateChanges == null || stateChanges.isEmpty()) {
            return inventory;
        }

        Inventory current = inventory;
        for (GameResult.StateChange stateChange : stateChanges) {
            Objects.requireNonNull(stateChange, "stateChange는 null일 수 없습니다.");
            if (stateChange instanceof GameResult.ItemAcquired acquired) {
                current = current.acquire(acquired.item());
                int actual = current.requireItem(acquired.item().id()).quantity();
                if (actual != acquired.resultingQuantity()) {
                    throw new IllegalStateException("ItemAcquired audit 결과 quantity가 canonical replay와 일치하지 않습니다.");
                }
            } else if (stateChange instanceof GameResult.ItemRemoved removed) {
                if (!current.requireItem(removed.item().id()).equals(removed.item())) {
                    throw new IllegalStateException("ItemRemoved audit의 기존 item이 canonical state와 일치하지 않습니다.");
                }
                current = current.remove(removed.item().id());
            } else if (stateChange instanceof GameResult.ItemQuantityChanged quantityChanged) {
                OwnedItem existing = current.requireItem(quantityChanged.itemId());
                requireDefinition(existing, quantityChanged.definitionId());
                if (existing.quantity() != quantityChanged.previousQuantity()) {
                    throw new IllegalStateException("ItemQuantityChanged audit의 이전 quantity가 canonical state와 일치하지 않습니다.");
                }
                current = current.changeQuantity(quantityChanged.itemId(), quantityChanged.nextQuantity());
            } else if (stateChange instanceof GameResult.ItemConsumed consumed) {
                OwnedItem existing = current.requireItem(consumed.itemId());
                requireDefinition(existing, consumed.definitionId());
                current = current.consume(consumed.itemId(), consumed.quantity());
                OwnedItem remaining = current.items().get(consumed.itemId());
                int actualRemaining = remaining == null ? 0 : remaining.quantity();
                if (actualRemaining != consumed.remainingQuantity()) {
                    throw new IllegalStateException("ItemConsumed audit의 남은 quantity가 canonical replay와 일치하지 않습니다.");
                }
            } else if (stateChange instanceof GameResult.ItemEquipped equipped) {
                OwnedItem existing = current.requireItem(equipped.itemId());
                requireDefinition(existing, equipped.definitionId());
                current = current.equip(equipped.itemId(), equipped.slot());
            } else if (stateChange instanceof GameResult.ItemUnequipped unequipped) {
                String equippedItemId = current.equipment().itemIdAt(unequipped.slot());
                if (!unequipped.itemId().equals(equippedItemId)) {
                    throw new IllegalStateException("ItemUnequipped audit가 canonical equipment와 일치하지 않습니다.");
                }
                OwnedItem existing = current.requireItem(unequipped.itemId());
                requireDefinition(existing, unequipped.definitionId());
                current = current.unequip(unequipped.slot());
            }
        }
        return current;
    }

    private Applied applyOne(Inventory inventory, InventoryCommand command) {
        if (command instanceof InventoryCommand.Acquire acquire) {
            Inventory next = inventory.acquire(acquire.item());
            int resultingQuantity = next.requireItem(acquire.item().id()).quantity();
            return new Applied(next, new GameResult.ItemAcquired(acquire.item(), resultingQuantity));
        }
        if (command instanceof InventoryCommand.Remove remove) {
            OwnedItem removed = inventory.requireItem(remove.itemId());
            return new Applied(inventory.remove(remove.itemId()), new GameResult.ItemRemoved(removed));
        }
        if (command instanceof InventoryCommand.ChangeQuantity changeQuantity) {
            OwnedItem existing = inventory.requireItem(changeQuantity.itemId());
            Inventory next = inventory.changeQuantity(changeQuantity.itemId(), changeQuantity.newQuantity());
            return new Applied(next, new GameResult.ItemQuantityChanged(
                    existing.id(), existing.definition().id(), existing.quantity(), changeQuantity.newQuantity()
            ));
        }
        if (command instanceof InventoryCommand.Consume consume) {
            OwnedItem existing = inventory.requireItem(consume.itemId());
            Inventory next = inventory.consume(consume.itemId(), consume.quantity());
            OwnedItem remaining = next.items().get(consume.itemId());
            return new Applied(next, new GameResult.ItemConsumed(
                    existing.id(), existing.definition().id(), consume.quantity(), remaining == null ? 0 : remaining.quantity()
            ));
        }
        if (command instanceof InventoryCommand.Equip equip) {
            OwnedItem existing = inventory.requireItem(equip.itemId());
            return new Applied(
                    inventory.equip(equip.itemId(), equip.slot()),
                    new GameResult.ItemEquipped(equip.slot(), existing.id(), existing.definition().id())
            );
        }
        if (command instanceof InventoryCommand.Unequip unequip) {
            String itemId = inventory.equipment().itemIdAt(unequip.slot());
            if (itemId == null) {
                throw new IllegalStateException("비어 있는 equipment slot은 해제할 수 없습니다: " + unequip.slot());
            }
            OwnedItem existing = inventory.requireItem(itemId);
            return new Applied(
                    inventory.unequip(unequip.slot()),
                    new GameResult.ItemUnequipped(unequip.slot(), existing.id(), existing.definition().id())
            );
        }
        throw new IllegalArgumentException("지원하지 않는 inventory command입니다: " + command.getClass().getName());
    }

    private static void requireDefinition(OwnedItem item, String definitionId) {
        if (!item.definition().id().equals(definitionId)) {
            throw new IllegalStateException("inventory audit의 item definition이 canonical state와 일치하지 않습니다.");
        }
    }

    public record Result(Inventory inventory, List<GameResult.StateChange> stateChanges) {
        public Result {
            Objects.requireNonNull(inventory, "inventory는 필수입니다.");
            stateChanges = stateChanges == null ? List.of() : List.copyOf(stateChanges);
        }
    }

    private record Applied(Inventory inventory, GameResult.StateChange stateChange) {
    }
}
