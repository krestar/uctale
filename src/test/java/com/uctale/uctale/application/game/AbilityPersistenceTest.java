package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.game.AbilityRules;
import com.uctale.uctale.domain.game.CharacterVitals;
import com.uctale.uctale.domain.game.CombatEncounter;
import com.uctale.uctale.domain.game.CombatRules;
import com.uctale.uctale.domain.game.EnemyState;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.TurnResolution;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AbilityPersistenceTest {
    @Test
    void combatAuditRoundTrip_ReplaysAbilityCooldown() {
        GameState state = activeCombat();
        PlayerAction action = new PlayerAction(1, "token", ActionType.COMBAT_ABILITY, 1,
                Map.of("encounterId", "enc-1", "abilityDefinitionId", "arcane-bolt", "targetId", "wolf"), "마법탄");
        TurnResolution resolution = new com.uctale.uctale.domain.game.ActionResolver().resolve(state, action);
        CombatAuditCodec codec = new CombatAuditCodec(new ObjectMapper());

        var decoded = codec.deserialize(codec.serialize(resolution.gameResult().stateChanges()));

        assertThat(AbilityRules.replay(state.abilityState(), decoded))
                .isEqualTo(resolution.stateTransition().nextState().abilityState());
        assertThat(decoded).anyMatch(com.uctale.uctale.domain.game.GameResult.AbilityResolved.class::isInstance);
    }

    private GameState activeCombat() {
        GameState state = GameState.initial("세계", "캐릭터", "오프닝");
        EnemyState enemy = new EnemyState("wolf", "늑대", CharacterVitals.defaults());
        CombatEncounter pending = CombatRules.start(null, "enc-1", List.of(enemy)).encounter();
        return state.withCombatEncounter(CombatRules.activate(pending, state.playerCharacter().vitals()).encounter());
    }
}
