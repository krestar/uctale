package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.EquipmentSlot;
import com.uctale.uctale.domain.game.GameResult;
import com.uctale.uctale.domain.game.ItemDefinition;
import com.uctale.uctale.domain.game.ItemOwnershipType;
import com.uctale.uctale.domain.game.OwnedItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryAuditCodecTest {
    private final InventoryAuditCodec codec = new InventoryAuditCodec(new ObjectMapper());

    @Test
    @DisplayName("inventory state change만 GameLog audit JSON으로 round-trip한다")
    void roundTrip_InventoryChangesOnly() {
        OwnedItem sword = new OwnedItem("sword:001",
                new ItemDefinition("iron-sword", ItemOwnershipType.INSTANCE, EquipmentSlot.MAIN_HAND), 1);
        List<GameResult.StateChange> changes = List.of(
                new GameResult.ItemAcquired(sword, 1),
                new GameResult.ItemEquipped(EquipmentSlot.MAIN_HAND, "sword:001", "iron-sword"),
                new GameResult.TurnAdvanced(1, 2));
        String json = codec.serialize(changes);
        assertThat(json).contains("ITEM_ACQUIRED", "ITEM_EQUIPPED").doesNotContain("TurnAdvanced");
        assertThat(codec.deserialize(json)).containsExactly(changes.get(0), changes.get(1));
    }

    @Test
    @DisplayName("inventory change가 없으면 legacy 호환을 위해 null audit을 저장한다")
    void noInventoryChanges_SerializesAsNull() {
        assertThat(codec.serialize(List.of(new GameResult.TurnAdvanced(1, 2)))).isNull();
        assertThat(codec.deserialize(null)).isEmpty();
    }

    @Test
    @DisplayName("손상되거나 미래 type의 audit은 조용히 무시하지 않는다")
    void damagedAudit_FailsExplicitly() {
        assertThatThrownBy(() -> codec.deserialize("{\"type\":\"ITEM_ACQUIRED\"}"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JSON array");
        assertThatThrownBy(() -> codec.deserialize("[{\"type\":\"FUTURE_ITEM_CHANGE\"}]"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("지원하지 않는");
    }
}
