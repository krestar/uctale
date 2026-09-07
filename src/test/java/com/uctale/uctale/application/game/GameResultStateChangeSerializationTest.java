package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.EquipmentSlot;
import com.uctale.uctale.domain.game.GameResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameResultStateChangeSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("NarrativeContext JSON에서 equip과 unequip state change type을 구분할 수 있다")
    void stateChanges_ExposeExplicitTypeForProviderProjection() throws Exception {
        String json = objectMapper.writeValueAsString(List.of(
                new GameResult.ItemEquipped(EquipmentSlot.MAIN_HAND, "sword:001", "iron-sword"),
                new GameResult.ItemUnequipped(EquipmentSlot.MAIN_HAND, "sword:001", "iron-sword")
        ));

        assertThat(json).contains("\"type\":\"ITEM_EQUIPPED\"");
        assertThat(json).contains("\"type\":\"ITEM_UNEQUIPPED\"");
    }
}
