package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.UseAbilityAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AbilityRules {
    private final VitalsRules vitalsRules = new VitalsRules();

    public Result resolve(GameState state, UseAbilityAction action) {
        Objects.requireNonNull(state, "GameState는 필수입니다.");
        Objects.requireNonNull(action, "UseAbilityAction은 필수입니다.");
        CombatEncounter encounter = requirePlayerTurn(state, action.encounterId());
        AbilityDefinition definition = AbilityDefinitions.require(action.abilityDefinitionId());
        if (state.abilityState().remainingCooldown(definition.definitionId()) > 0) {
            throw new IllegalArgumentException("ability가 cooldown 중입니다: " + definition.definitionId());
        }
        CharacterVitals currentVitals = state.playerCharacter().vitals();
        if (currentVitals.mp().current() < definition.mpCost()) {
            throw new IllegalArgumentException("ability 사용에 필요한 MP가 부족합니다.");
        }
        validateTarget(encounter, definition, action.targetId(), currentVitals);

        CooldownAdvance cooldownAdvance = advanceCooldowns(state.abilityState());
        List<VitalsCommand> vitalsCommands = new ArrayList<>();
        if (definition.mpCost() > 0) vitalsCommands.add(new VitalsCommand.SpendMana(definition.mpCost()));
        if (definition.effectType() == AbilityEffectType.HEAL) {
            vitalsCommands.add(new VitalsCommand.Heal(definition.effectAmount()));
        } else if (definition.effectType() == AbilityEffectType.STATUS) {
            vitalsCommands.add(new VitalsCommand.ApplyStatus(definition.statusEffect()));
        }
        vitalsCommands.add(new VitalsCommand.AdvanceStatusDurations(StatusExpiryTrigger.END_OF_TURN));
        VitalsRules.Result vitalsResult = vitalsRules.apply(currentVitals, vitalsCommands);

        CombatEncounter nextCombat = encounter;
        List<GameResult.StateChange> combatChanges = new ArrayList<>();
        int appliedAmount;
        String statusDefinitionId = null;
        Integer hpBefore = null;
        Integer hpAfter = null;
        if (definition.effectType() == AbilityEffectType.DAMAGE) {
            EnemyState target = encounter.requireEnemy(action.targetId());
            hpBefore = target.vitals().hp().current();
            hpAfter = (int) Math.max(0L, (long) hpBefore - definition.effectAmount());
            appliedAmount = hpBefore - hpAfter;
            CharacterVitals damaged = target.vitals().withHp(target.vitals().hp().withCurrent(hpAfter));
            CombatRules.Result updated = CombatRules.updateEnemy(nextCombat, target.withVitals(damaged), vitalsResult.vitals());
            nextCombat = updated.encounter();
            combatChanges.addAll(updated.stateChanges());
        } else if (definition.effectType() == AbilityEffectType.HEAL) {
            hpBefore = currentVitals.hp().current();
            hpAfter = vitalsResult.vitals().hp().current();
            appliedAmount = hpAfter - hpBefore;
        } else {
            appliedAmount = definition.statusEffect().intensity();
            statusDefinitionId = definition.statusEffect().definitionId();
        }
        if (nextCombat.active()) {
            CombatRules.Result advanced = CombatRules.advanceActor(nextCombat, vitalsResult.vitals());
            nextCombat = advanced.encounter();
            combatChanges.addAll(advanced.stateChanges());
        }

        AbilityState nextAbilityState = cooldownAdvance.state().withCooldown(definition.definitionId(), definition.cooldownTurns());
        GameResult.AbilityCooldownChanged usedCooldown = new GameResult.AbilityCooldownChanged(
                definition.definitionId(), 0, definition.cooldownTurns(), GameResult.AbilityCooldownChangeReason.USED);
        AbilityResult abilityResult = new AbilityResult(
                definition.definitionId(), definition.targetRule(), action.targetId(), definition.mpCost(),
                currentVitals.mp().current(), vitalsResult.vitals().mp().current(), definition.cooldownTurns(),
                definition.effectType(), appliedAmount, statusDefinitionId, hpBefore, hpAfter);

        List<GameResult.StateChange> changes = new ArrayList<>(vitalsResult.stateChanges());
        changes.addAll(combatChanges);
        changes.addAll(cooldownAdvance.stateChanges());
        changes.add(new GameResult.AbilityResolved(abilityResult));
        changes.add(usedCooldown);
        return new Result(vitalsResult.vitals(), nextCombat, nextAbilityState, abilityResult, List.copyOf(changes));
    }

    public CooldownAdvance advanceCooldowns(AbilityState state) {
        Objects.requireNonNull(state, "AbilityState는 필수입니다.");
        AbilityState next = state;
        List<GameResult.StateChange> changes = new ArrayList<>();
        for (var entry : state.cooldowns().entrySet()) {
            int after = entry.getValue() - 1;
            next = next.withCooldown(entry.getKey(), after);
            changes.add(new GameResult.AbilityCooldownChanged(
                    entry.getKey(), entry.getValue(), after, GameResult.AbilityCooldownChangeReason.TURN_ENDED));
        }
        return new CooldownAdvance(next, List.copyOf(changes));
    }

    public static AbilityState replay(AbilityState state, List<GameResult.StateChange> stateChanges) {
        Objects.requireNonNull(state, "AbilityState는 필수입니다.");
        AbilityState current = state;
        if (stateChanges == null) return current;
        for (GameResult.StateChange change : stateChanges) {
            if (!(change instanceof GameResult.AbilityCooldownChanged cooldown)) continue;
            int actual = current.remainingCooldown(cooldown.definitionId());
            if (actual != cooldown.previousRemainingTurns()) {
                throw new IllegalStateException("ability cooldown audit의 이전 상태가 canonical state와 일치하지 않습니다.");
            }
            current = current.withCooldown(cooldown.definitionId(), cooldown.nextRemainingTurns());
        }
        return current;
    }

    private CombatEncounter requirePlayerTurn(GameState state, String encounterId) {
        CombatEncounter encounter = state.combatEncounter();
        if (encounter == null || !encounter.active() || !encounter.encounterId().equals(encounterId)) {
            throw new IllegalArgumentException("현재 active combat encounter와 ability action이 일치하지 않습니다.");
        }
        if (!CombatEncounter.PLAYER_ACTOR_ID.equals(encounter.currentActorId()) || state.playerCharacter().vitals().incapacitated()) {
            throw new IllegalArgumentException("현재 player는 ability를 사용할 수 없습니다.");
        }
        return encounter;
    }

    private void validateTarget(CombatEncounter encounter, AbilityDefinition definition, String targetId, CharacterVitals vitals) {
        if (definition.targetRule() == AbilityTargetRule.SELF) {
            if (!CombatEncounter.PLAYER_ACTOR_ID.equals(targetId)) throw new IllegalArgumentException("SELF ability target은 player여야 합니다.");
            if (definition.effectType() == AbilityEffectType.HEAL && vitals.hp().current() == vitals.hp().max()) {
                throw new IllegalArgumentException("최대 HP에서는 heal ability를 사용할 수 없습니다.");
            }
            if (definition.effectType() == AbilityEffectType.STATUS
                    && vitals.statusEffects().containsKey(definition.statusEffect().definitionId())) {
                throw new IllegalArgumentException("같은 status effect가 이미 적용되어 있습니다.");
            }
            return;
        }
        EnemyState target = encounter.requireEnemy(targetId);
        if (target.defeated()) throw new IllegalArgumentException("defeated enemy는 ability target이 될 수 없습니다.");
    }

    public record Result(CharacterVitals playerVitals, CombatEncounter combatEncounter, AbilityState abilityState,
                         AbilityResult abilityResult, List<GameResult.StateChange> stateChanges) {}
    public record CooldownAdvance(AbilityState state, List<GameResult.StateChange> stateChanges) {}
}
