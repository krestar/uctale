package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.AvailableAction;
import com.uctale.uctale.domain.game.AbilityDefinition;
import com.uctale.uctale.domain.game.AbilityDefinitions;
import com.uctale.uctale.domain.game.AbilityEffectType;
import com.uctale.uctale.domain.game.AbilityTargetRule;
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
        Objects.requireNonNull(state, "GameState는 필수입니다.");
        CombatEncounter encounter = state.combatEncounter();
        if (encounter == null || !encounter.active() || !CombatEncounter.PLAYER_ACTOR_ID.equals(encounter.currentActorId())
                || state.playerCharacter().vitals().incapacitated()) return List.of();

        List<AvailableAction> actions = new ArrayList<>();
        int choiceId = 1;
        for (EnemyState enemy : encounter.enemies().values()) {
            if (!enemy.defeated()) {
                actions.add(new AvailableAction(choiceId++, UUID.randomUUID().toString(), ActionType.COMBAT_ATTACK,
                        state.turnNumber(), Map.of("encounterId", encounter.encounterId(), "targetEnemyId", enemy.enemyId()),
                        enemy.displayName() + "을 공격한다"));
            }
        }
        for (AbilityDefinition definition : AbilityDefinitions.all()) {
            if (!usable(state, definition)) continue;
            if (definition.targetRule() == AbilityTargetRule.SELF) {
                actions.add(abilityAction(choiceId++, state, encounter, definition, CombatEncounter.PLAYER_ACTOR_ID,
                        definition.definitionId() + " 능력을 사용한다"));
            } else {
                for (EnemyState enemy : encounter.enemies().values()) {
                    if (!enemy.defeated()) {
                        actions.add(abilityAction(choiceId++, state, encounter, definition, enemy.enemyId(),
                                enemy.displayName() + "에게 " + definition.definitionId() + " 능력을 사용한다"));
                    }
                }
            }
        }
        Map<String, String> args = Map.of("encounterId", encounter.encounterId());
        actions.add(new AvailableAction(choiceId++, UUID.randomUUID().toString(), ActionType.COMBAT_PASS,
                state.turnNumber(), args, "전투 태세를 유지한다"));
        actions.add(new AvailableAction(choiceId, UUID.randomUUID().toString(), ActionType.COMBAT_ESCAPE,
                state.turnNumber(), args, "전투에서 이탈한다"));
        return List.copyOf(actions);
    }

    private boolean usable(GameState state, AbilityDefinition definition) {
        if (state.abilityState().remainingCooldown(definition.definitionId()) > 0) return false;
        if (state.playerCharacter().vitals().mp().current() < definition.mpCost()) return false;
        if (definition.effectType() == AbilityEffectType.HEAL
                && state.playerCharacter().vitals().hp().current() == state.playerCharacter().vitals().hp().max()) return false;
        return definition.effectType() != AbilityEffectType.STATUS
                || !state.playerCharacter().vitals().statusEffects().containsKey(definition.statusEffect().definitionId());
    }

    private AvailableAction abilityAction(int choiceId, GameState state, CombatEncounter encounter,
                                          AbilityDefinition definition, String targetId, String displayText) {
        return new AvailableAction(choiceId, UUID.randomUUID().toString(), ActionType.COMBAT_ABILITY, state.turnNumber(),
                Map.of("encounterId", encounter.encounterId(), "abilityDefinitionId", definition.definitionId(), "targetId", targetId),
                displayText);
    }
}
