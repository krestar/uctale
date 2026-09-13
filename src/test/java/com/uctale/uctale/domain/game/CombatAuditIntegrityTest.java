package com.uctale.uctale.domain.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CombatAuditIntegrityTest {

    @Test
    @DisplayName("ENEMY_UPDATED audit으로 참가자를 몰래 추가할 수 없다")
    void enemyUpdated_CannotChangeParticipantSet() {
        EnemyState a = new EnemyState("a", "A", CharacterVitals.defaults());
        EnemyState b = new EnemyState("b", "B", CharacterVitals.defaults());
        CombatEncounter previous = new CombatEncounter(
                "enc-1", CombatEncounterStatus.ACTIVE, Map.of("a", a), List.of("player", "a"), "player"
        );
        CombatEncounter corrupted = new CombatEncounter(
                "enc-1", CombatEncounterStatus.ACTIVE, Map.of("a", a, "b", b),
                List.of("player", "a", "b"), "player"
        );

        assertThatThrownBy(() -> new GameResult.CombatEncounterChanged(
                previous, corrupted, CombatChangeReason.ENEMY_UPDATED
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("참가자 집합");
    }
}
