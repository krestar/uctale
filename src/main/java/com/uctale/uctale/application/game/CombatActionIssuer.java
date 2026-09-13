package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.AvailableAction;
import com.uctale.uctale.domain.game.CombatEncounter;
import com.uctale.uctale.domain.game.GameState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public final class CombatActionIssuer {

    public List<AvailableAction> issue(GameState state) {
        Objects.requireNonNull(state, "GameState는 필수입니다.");
        CombatEncounter encounter = state.combatEncounter();
        if (encounter == null || !encounter.active()) return List.of();
        if (!CombatEncounter.PLAYER_ACTOR_ID.equals(encounter.currentActorId())
                || state.playerCharacter().vitals().incapacitated()) {
            return List.of();
        }
        Map<String, String> arguments = Map.of("encounterId", encounter.encounterId());
        return List.of(
                new AvailableAction(1, UUID.randomUUID().toString(), ActionType.COMBAT_PASS,
                        state.turnNumber(), arguments, "전투 태세를 유지한다"),
                new AvailableAction(2, UUID.randomUUID().toString(), ActionType.COMBAT_ESCAPE,
                        state.turnNumber(), arguments, "전투에서 이탈한다")
        );
    }
}
