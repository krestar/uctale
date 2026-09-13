package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.game.CharacterVitals;
import com.uctale.uctale.domain.game.CombatEncounter;
import com.uctale.uctale.domain.game.CombatRules;
import com.uctale.uctale.domain.game.EnemyState;
import com.uctale.uctale.domain.game.GameState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CombatActionIssuerTest {
    private final CombatActionIssuer issuer = new CombatActionIssuer();

    @Test
    @DisplayName("server-issued combat actions는 사용 가능한 Attack/Ability와 pass/escape를 발급한다")
    void issue_OnlyForActivePlayerTurn() {
        GameState initial = GameState.initial("세계", "캐릭터", "오프닝");
        assertThat(issuer.issue(initial)).isEmpty();
        CombatEncounter pending = CombatEncounter.pending("enc-1", List.of(new EnemyState("e1", "적", CharacterVitals.defaults())));
        assertThat(issuer.issue(initial.withCombatEncounter(pending))).isEmpty();
        GameState active = initial.withCombatEncounter(CombatRules.activate(pending, initial.playerCharacter().vitals()).encounter());

        var actions = issuer.issue(active);

        assertThat(actions).extracting(a -> a.type()).containsExactly(
                ActionType.COMBAT_ATTACK,
                ActionType.COMBAT_ABILITY,
                ActionType.COMBAT_ABILITY,
                ActionType.COMBAT_PASS,
                ActionType.COMBAT_ESCAPE
        );
        assertThat(actions.getFirst().arguments()).containsEntry("encounterId", "enc-1").containsEntry("targetEnemyId", "e1");
        assertThat(actions.stream().filter(a -> a.type() == ActionType.COMBAT_ABILITY))
                .allMatch(a -> a.arguments().containsKey("abilityDefinitionId") && a.arguments().containsKey("targetId"));
    }
}
