package com.uctale.uctale.domain.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryRulesTest {
    private final InventoryRules rules = new InventoryRules();

    @Test
    @DisplayName("신규 inventory는 비어 있고 획득/수량변경/소비가 typed state change로 결정된다")
    void stackTransitions_AreDeterministicAndAuditable() {
        OwnedItem potion = stack("stack:potion", "potion", null, 3);
        InventoryRules.Result acquired = rules.apply(Inventory.empty(), List.of(new InventoryCommand.Acquire(potion)));
        InventoryRules.Result changed = rules.apply(acquired.inventory(), List.of(
                new InventoryCommand.ChangeQuantity("stack:potion", 5), new InventoryCommand.Consume("stack:potion", 2)));
        assertThat(acquired.inventory().requireItem("stack:potion").quantity()).isEqualTo(3);
        assertThat(acquired.stateChanges()).containsExactly(new GameResult.ItemAcquired(potion, 3));
        assertThat(changed.inventory().requireItem("stack:potion").quantity()).isEqualTo(3);
        assertThat(changed.stateChanges()).containsExactly(
                new GameResult.ItemQuantityChanged("stack:potion", "potion", 3, 5),
                new GameResult.ItemConsumed("stack:potion", "potion", 2, 3));
        assertThat(InventoryRules.replay(Inventory.empty(), acquired.stateChanges())).isEqualTo(acquired.inventory());
        assertThat(InventoryRules.replay(acquired.inventory(), changed.stateChanges())).isEqualTo(changed.inventory());
    }

    @Test
    @DisplayName("stack 획득은 같은 definition과 owned id만 합산하고 overflow를 거절한다")
    void acquireStack_ValidatesIdentityAndOverflow() {
        Inventory inventory = Inventory.empty().acquire(stack("stack:potion", "potion", null, Integer.MAX_VALUE));
        assertThatThrownBy(() -> inventory.acquire(stack("stack:potion", "potion", null, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("지원 범위");
        assertThatThrownBy(() -> Inventory.empty().acquire(stack("stack:potion", "potion", null, 1))
                .acquire(stack("stack:potion", "elixir", null, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("definition");
    }

    @Test
    @DisplayName("instance item은 owned id별 단일 인스턴스로 유지되고 quantity 변경을 허용하지 않는다")
    void instanceItems_KeepStableOwnedIdentity() {
        ItemDefinition sword = new ItemDefinition("iron-sword", ItemOwnershipType.INSTANCE, EquipmentSlot.MAIN_HAND);
        Inventory inventory = Inventory.empty().acquire(new OwnedItem("sword:001", sword, 1)).acquire(new OwnedItem("sword:002", sword, 1));
        assertThat(inventory.items()).containsOnlyKeys("sword:001", "sword:002");
        assertThatThrownBy(() -> inventory.changeQuantity("sword:001", 2)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("INSTANCE");
        assertThatThrownBy(() -> new OwnedItem("sword:003", sword, 2)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("quantity");
    }

    @Test
    @DisplayName("같은 definition ID를 서로 다른 의미로 소유할 수 없다")
    void inventory_RejectsConflictingDefinitionIdentity() {
        ItemDefinition mainHand = new ItemDefinition("artifact", ItemOwnershipType.INSTANCE, EquipmentSlot.MAIN_HAND);
        ItemDefinition body = new ItemDefinition("artifact", ItemOwnershipType.INSTANCE, EquipmentSlot.BODY);
        assertThatThrownBy(() -> new Inventory(Map.of(
                "artifact:001", new OwnedItem("artifact:001", mainHand, 1),
                "artifact:002", new OwnedItem("artifact:002", body, 1)), Equipment.empty()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("definition id");
    }

    @Test
    @DisplayName("장착 가능한 definition은 개별 INSTANCE ownership만 허용한다")
    void equippableDefinition_MustBeInstance() {
        assertThatThrownBy(() -> new ItemDefinition("stack-sword", ItemOwnershipType.STACK, EquipmentSlot.MAIN_HAND))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("INSTANCE");
    }

    @Test
    @DisplayName("장착은 존재하는 item의 정의된 slot만 허용하고 해제 결과도 audit한다")
    void equipmentTransitions_EnforceSlotInvariant() {
        OwnedItem sword = instance("sword:001", "iron-sword", EquipmentSlot.MAIN_HAND);
        Inventory inventory = Inventory.empty().acquire(sword);
        assertThatThrownBy(() -> inventory.equip("sword:001", EquipmentSlot.BODY)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("slot");
        InventoryRules.Result equipped = rules.apply(inventory, List.of(new InventoryCommand.Equip("sword:001", EquipmentSlot.MAIN_HAND)));
        InventoryRules.Result unequipped = rules.apply(equipped.inventory(), List.of(new InventoryCommand.Unequip(EquipmentSlot.MAIN_HAND)));
        assertThat(equipped.inventory().equipment().itemIdAt(EquipmentSlot.MAIN_HAND)).isEqualTo("sword:001");
        assertThat(equipped.stateChanges()).containsExactly(new GameResult.ItemEquipped(EquipmentSlot.MAIN_HAND, "sword:001", "iron-sword"));
        assertThat(unequipped.inventory().equipment()).isEqualTo(Equipment.empty());
        assertThat(unequipped.stateChanges()).containsExactly(new GameResult.ItemUnequipped(EquipmentSlot.MAIN_HAND, "sword:001", "iron-sword"));
    }

    @Test
    @DisplayName("같은 item의 중복 장착과 사용 중 slot 덮어쓰기를 거절한다")
    void equipment_RejectsDuplicateOwnershipReferences() {
        assertThatThrownBy(() -> new Equipment(Map.of(EquipmentSlot.MAIN_HAND, "same-item", EquipmentSlot.OFF_HAND, "same-item")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("둘 이상의 slot");
        Inventory inventory = Inventory.empty().acquire(instance("sword:001", "iron-sword", EquipmentSlot.MAIN_HAND))
                .acquire(instance("sword:002", "steel-sword", EquipmentSlot.MAIN_HAND)).equip("sword:001", EquipmentSlot.MAIN_HAND);
        assertThatThrownBy(() -> inventory.equip("sword:002", EquipmentSlot.MAIN_HAND)).isInstanceOf(IllegalStateException.class).hasMessageContaining("사용 중");
    }

    @Test
    @DisplayName("음수/0 수량, 존재하지 않는 item, 보유량 초과 소비를 거절한다")
    void invalidQuantityAndMissingItem_AreRejected() {
        assertThatThrownBy(() -> stack("bad", "potion", null, 0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("quantity");
        assertThatThrownBy(() -> new InventoryCommand.Consume("missing", 0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("quantity");
        assertThatThrownBy(() -> Inventory.empty().consume("missing", 1)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("존재하지 않는");
        Inventory inventory = Inventory.empty().acquire(stack("stack:potion", "potion", null, 2));
        assertThatThrownBy(() -> inventory.consume("stack:potion", 3)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("보유 quantity");
        assertThatThrownBy(() -> inventory.changeQuantity("stack:potion", 0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 이상");
        assertThatThrownBy(() -> inventory.changeQuantity("stack:potion", 2)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("실제로 변경");
    }

    @Test
    @DisplayName("장착 중 item은 명시적 해제 없이 제거하거나 전량 소비할 수 없다")
    void equippedItem_CannotDisappearImplicitly() {
        Inventory equipped = Inventory.empty().acquire(instance("sword:001", "iron-sword", EquipmentSlot.MAIN_HAND)).equip("sword:001", EquipmentSlot.MAIN_HAND);
        assertThatThrownBy(() -> equipped.remove("sword:001")).isInstanceOf(IllegalStateException.class).hasMessageContaining("장착 중");
        assertThatThrownBy(() -> equipped.consume("sword:001", 1)).isInstanceOf(IllegalStateException.class).hasMessageContaining("장착 중");
    }

    @Test
    @DisplayName("손상된 audit은 canonical state와 불일치하면 replay에서 실패한다")
    void replay_RejectsTamperedAudit() {
        Inventory inventory = Inventory.empty().acquire(stack("stack:potion", "potion", null, 2));
        GameResult.ItemConsumed tampered = new GameResult.ItemConsumed("stack:potion", "potion", 1, 0);
        assertThatThrownBy(() -> InventoryRules.replay(inventory, List.of(tampered))).isInstanceOf(IllegalStateException.class).hasMessageContaining("남은 quantity");
    }

    private OwnedItem stack(String ownedId, String definitionId, EquipmentSlot slot, int quantity) {
        return new OwnedItem(ownedId, new ItemDefinition(definitionId, ItemOwnershipType.STACK, slot), quantity);
    }
    private OwnedItem instance(String ownedId, String definitionId, EquipmentSlot slot) {
        return new OwnedItem(ownedId, new ItemDefinition(definitionId, ItemOwnershipType.INSTANCE, slot), 1);
    }
}
