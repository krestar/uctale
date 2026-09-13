package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.AvailableAction;
import com.uctale.uctale.domain.game.CombatEncounter;
import com.uctale.uctale.domain.game.EnemyState;
import com.uctale.uctale.domain.game.GameState;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public final class CombatActionIssuer {
    public List<AvailableAction> issue(GameState state) {
        Objects.requireNonNull(state, "GameState는 필수입니다."); CombatEncounter encounter = state.combatEncounter();
        if (encounter == null || !encounter.active() || !CombatEncounter.PLAYER_ACTOR_ID.equals(encounter.currentActorId()) || state.playerCharacter().vitals().incapacitated()) return List.of();
        List<AvailableAction> actions = new ArrayList<>(); int choiceId = 1;
        for (EnemyState enemy : encounter.enemies().values()) if (!enemy.defeated()) actions.add(new AvailableAction(choiceId++, UUID.randomUUID().toString(), ActionType.COMBAT_ATTACK, state.turnNumber(), Map.of("encounterId", encounter.encounterId(), "targetEnemyId", enemy.enemyId()), enemy.displayName() + "을 공격한다"));
        Map<String,String> args = Map.of("encounterId", encounter.encounterId());
        actions.add(new AvailableAction(choiceId++, UUID.randomUUID().toString(), ActionType.COMBAT_PASS, state.turnNumber(), args, "전투 태세를 유지한다"));
        actions.add(new AvailableAction(choiceId, UUID.randomUUID().toString(), ActionType.COMBAT_ESCAPE, state.turnNumber(), args, "전투에서 이탈한다"));
        return List.copyOf(actions);
    }
}
