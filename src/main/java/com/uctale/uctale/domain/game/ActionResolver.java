package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.AttackAction;
import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.action.UseAbilityAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ActionResolver {

    private final InventoryRules inventoryRules = new InventoryRules();
    private final VitalsRules vitalsRules = new VitalsRules();
    private final AttackRules attackRules = new AttackRules();
    private final AbilityRules abilityRules = new AbilityRules();

    public boolean requiresSkillCheck(PlayerAction action) {
        Objects.requireNonNull(action, "PlayerAction은 필수입니다.");
        return action.type() == ActionType.SKILL_CHECK;
    }

    public boolean requiresAttackRoll(PlayerAction action) {
        Objects.requireNonNull(action, "PlayerAction은 필수입니다.");
        return action.type() == ActionType.COMBAT_ATTACK;
    }

    public SkillCheckResult rollSkillCheck(GameState state, PlayerAction action, RandomSource randomSource) {
        validateBase(state, action);
        if (action.type() != ActionType.SKILL_CHECK) throw new IllegalArgumentException("Skill Check가 필요하지 않은 action입니다.");
        SkillCheck skillCheck = parseSkillCheck(action);
        return skillCheck.resolve(state.playerCharacter().stats(), randomSource);
    }

    public AttackResult rollAttack(GameState state, PlayerAction action, RandomSource randomSource) {
        validateBase(state, action);
        if (action.type() != ActionType.COMBAT_ATTACK) throw new IllegalArgumentException("Attack 판정이 필요하지 않은 action입니다.");
        AttackAction attackAction = parseAndValidateAttackAction(state, action);
        return attackRules.resolve(state, attackAction, randomSource);
    }

    public TurnResolution resolve(GameState state, PlayerAction action) {
        return resolveWithEffects(state, action, List.of(), List.of());
    }

    public TurnResolution resolveWithInventory(GameState state, PlayerAction action, List<InventoryCommand> inventoryCommands) {
        return resolveWithEffects(state, action, inventoryCommands, List.of());
    }

    public TurnResolution resolveWithVitals(GameState state, PlayerAction action, List<VitalsCommand> vitalsCommands) {
        return resolveWithEffects(state, action, List.of(), vitalsCommands);
    }

    public TurnResolution resolveWithEffects(GameState state, PlayerAction action,
            List<InventoryCommand> inventoryCommands, List<VitalsCommand> vitalsCommands) {
        validateBase(state, action);
        if (action.type() == ActionType.SKILL_CHECK) throw new IllegalArgumentException("SKILL_CHECK action에는 서버가 확정한 SkillCheckResult가 필요합니다.");
        if (action.type() == ActionType.COMBAT_ATTACK) throw new IllegalArgumentException("COMBAT_ATTACK action에는 서버가 확정한 AttackResult가 필요합니다.");
        if (action.type() == ActionType.COMBAT_ABILITY) {
            if ((inventoryCommands != null && !inventoryCommands.isEmpty()) || (vitalsCommands != null && !vitalsCommands.isEmpty())) {
                throw new IllegalArgumentException("COMBAT_ABILITY에는 외부 inventory/vitals command를 함께 적용할 수 없습니다.");
            }
            return resolvedAbility(state, action);
        }
        validateActionArguments(state, action);
        return resolved(state, action, null, inventoryCommands, vitalsCommands);
    }

    public TurnResolution resolve(GameState state, PlayerAction action, SkillCheckResult skillCheckResult) {
        return resolveWithEffects(state, action, skillCheckResult, List.of(), List.of());
    }

    public TurnResolution resolveWithInventory(GameState state, PlayerAction action, SkillCheckResult skillCheckResult,
            List<InventoryCommand> inventoryCommands) {
        return resolveWithEffects(state, action, skillCheckResult, inventoryCommands, List.of());
    }

    public TurnResolution resolveWithVitals(GameState state, PlayerAction action, SkillCheckResult skillCheckResult,
            List<VitalsCommand> vitalsCommands) {
        return resolveWithEffects(state, action, skillCheckResult, List.of(), vitalsCommands);
    }

    public TurnResolution resolveWithEffects(GameState state, PlayerAction action, SkillCheckResult skillCheckResult,
            List<InventoryCommand> inventoryCommands, List<VitalsCommand> vitalsCommands) {
        validateBase(state, action);
        if (action.type() != ActionType.SKILL_CHECK) {
            if (skillCheckResult != null) throw new IllegalArgumentException("Skill Check가 아닌 action에 판정 결과를 연결할 수 없습니다.");
            if (action.type() == ActionType.COMBAT_ATTACK) throw new IllegalArgumentException("COMBAT_ATTACK action에는 서버가 확정한 AttackResult가 필요합니다.");
            if (action.type() == ActionType.COMBAT_ABILITY) {
                if ((inventoryCommands != null && !inventoryCommands.isEmpty()) || (vitalsCommands != null && !vitalsCommands.isEmpty())) {
                    throw new IllegalArgumentException("COMBAT_ABILITY에는 외부 inventory/vitals command를 함께 적용할 수 없습니다.");
                }
                return resolvedAbility(state, action);
            }
            validateActionArguments(state, action);
            return resolved(state, action, null, inventoryCommands, vitalsCommands);
        }
        Objects.requireNonNull(skillCheckResult, "SkillCheckResult는 필수입니다.");
        SkillCheck skillCheck = parseSkillCheck(action);
        validateSkillCheckResult(state, skillCheck, skillCheckResult);
        return resolved(state, action, skillCheckResult, inventoryCommands, vitalsCommands);
    }

    public TurnResolution resolveAttack(GameState state, PlayerAction action, AttackResult attackResult) {
        validateBase(state, action);
        if (action.type() != ActionType.COMBAT_ATTACK) {
            if (attackResult != null) throw new IllegalArgumentException("COMBAT_ATTACK이 아닌 action에 AttackResult를 연결할 수 없습니다.");
            throw new IllegalArgumentException("COMBAT_ATTACK action이 필요합니다.");
        }
        Objects.requireNonNull(attackResult, "AttackResult는 필수입니다.");
        AttackAction attackAction = parseAndValidateAttackAction(state, action);
        attackRules.validateStored(state, attackAction, attackResult);
        return resolvedAttack(state, action, attackResult);
    }

    private TurnResolution resolved(GameState state, PlayerAction action, SkillCheckResult skillCheckResult,
            List<InventoryCommand> inventoryCommands, List<VitalsCommand> vitalsCommands) {
        InventoryRules.Result inventoryResult = inventoryRules.apply(state.inventory(), inventoryCommands);
        VitalsRules.Result vitalsResult = vitalsRules.apply(state.playerCharacter().vitals(), withTurnEndTiming(vitalsCommands));
        AbilityRules.CooldownAdvance cooldownAdvance = abilityRules.advanceCooldowns(state.abilityState());

        CombatEncounter nextCombat = state.combatEncounter();
        List<GameResult.StateChange> combatChanges = List.of();
        if (action.type() == ActionType.COMBAT_PASS) {
            CombatRules.Result result = CombatRules.advanceActor(nextCombat, vitalsResult.vitals());
            nextCombat = result.encounter();
            combatChanges = result.stateChanges();
        } else if (action.type() == ActionType.COMBAT_ESCAPE) {
            CombatRules.Result result = CombatRules.escape(nextCombat, vitalsResult.vitals());
            nextCombat = result.encounter();
            combatChanges = result.stateChanges();
        } else if (nextCombat != null && nextCombat.active()) {
            CombatRules.Result reconciliation = reconcilePlayerVitals(nextCombat, vitalsResult.vitals());
            if (reconciliation != null) {
                nextCombat = reconciliation.encounter();
                combatChanges = reconciliation.stateChanges();
            }
        }

        GameState nextState = state.withRuleState(inventoryResult.inventory(), vitalsResult.vitals(), nextCombat,
                cooldownAdvance.state()).advanceTurn();
        List<GameResult.GameEvent> events = new ArrayList<>();
        events.add(GameResult.GameEvent.ACTION_RESOLVED);
        if (skillCheckResult != null) events.add(GameResult.GameEvent.SKILL_CHECK_RESOLVED);
        if (isCombatAction(action.type())) events.add(GameResult.GameEvent.COMBAT_ACTION_RESOLVED);
        if (!combatChanges.isEmpty()) events.add(GameResult.GameEvent.COMBAT_ENCOUNTER_CHANGED);

        List<GameResult.StateChange> stateChanges = new ArrayList<>(inventoryResult.stateChanges());
        stateChanges.addAll(vitalsResult.stateChanges());
        stateChanges.addAll(combatChanges);
        stateChanges.addAll(cooldownAdvance.stateChanges());
        stateChanges.add(new GameResult.TurnAdvanced(state.turnNumber(), nextState.turnNumber()));
        GameResult result = new GameResult(action, GameResult.Outcome.RESOLVED, skillCheckResult, List.of(), events,
                stateChanges, List.of(action.displayText()));
        return new TurnResolution(result, new StateTransition(state, nextState));
    }

    private TurnResolution resolvedAttack(GameState state, PlayerAction action, AttackResult attackResult) {
        VitalsRules.Result playerVitals = vitalsRules.apply(state.playerCharacter().vitals(), withTurnEndTiming(List.of()));
        AbilityRules.CooldownAdvance cooldownAdvance = abilityRules.advanceCooldowns(state.abilityState());
        CombatEncounter nextCombat = state.combatEncounter();
        List<GameResult.StateChange> combatChanges = new ArrayList<>();
        combatChanges.add(new GameResult.AttackResolved(attackResult));

        if (attackResult.finalDamage() > 0) {
            EnemyState target = nextCombat.requireEnemy(attackResult.targetEnemyId());
            CharacterVitals damagedVitals = target.vitals().withHp(target.vitals().hp().withCurrent(attackResult.targetHpAfter()));
            CombatRules.Result updated = CombatRules.updateEnemy(nextCombat, target.withVitals(damagedVitals), playerVitals.vitals());
            nextCombat = updated.encounter();
            combatChanges.addAll(updated.stateChanges());
        }
        if (nextCombat.active()) {
            CombatRules.Result advanced = CombatRules.advanceActor(nextCombat, playerVitals.vitals());
            nextCombat = advanced.encounter();
            combatChanges.addAll(advanced.stateChanges());
        }

        GameState nextState = state.withRuleState(state.inventory(), playerVitals.vitals(), nextCombat,
                cooldownAdvance.state()).advanceTurn();
        List<GameResult.StateChange> stateChanges = new ArrayList<>();
        stateChanges.addAll(playerVitals.stateChanges());
        stateChanges.addAll(combatChanges);
        stateChanges.addAll(cooldownAdvance.stateChanges());
        stateChanges.add(new GameResult.TurnAdvanced(state.turnNumber(), nextState.turnNumber()));

        List<GameResult.GameEvent> events = new ArrayList<>();
        events.add(GameResult.GameEvent.ACTION_RESOLVED);
        events.add(GameResult.GameEvent.COMBAT_ACTION_RESOLVED);
        events.add(GameResult.GameEvent.ATTACK_RESOLVED);
        if (combatChanges.stream().anyMatch(GameResult.CombatEncounterChanged.class::isInstance)) events.add(GameResult.GameEvent.COMBAT_ENCOUNTER_CHANGED);

        String attackCue = "공격 판정은 서버 확정값을 그대로 따른다: target=%s, attackRoll=%d, statModifier=%d, "
                + "equipmentAttackModifier=%d, defense=%d, total=%d, outcome=%s, damageRoll=%d, "
                + "damageStatModifier=%d, equipmentDamageModifier=%d, mitigation=%d, finalDamage=%d, targetHp=%d->%d.";
        attackCue = attackCue.formatted(attackResult.targetEnemyId(), attackResult.attackRoll(), attackResult.statModifier(),
                attackResult.equipmentAttackModifier(), attackResult.defenseScore(), attackResult.attackTotal(), attackResult.outcome(),
                attackResult.damageRoll(), attackResult.damageStatModifier(), attackResult.equipmentDamageModifier(),
                attackResult.damageMitigation(), attackResult.finalDamage(), attackResult.targetHpBefore(), attackResult.targetHpAfter());
        GameResult result = new GameResult(action, GameResult.Outcome.RESOLVED, null, attackResult, List.of(), events,
                stateChanges, List.of(action.displayText(), attackCue));
        return new TurnResolution(result, new StateTransition(state, nextState));
    }

    private TurnResolution resolvedAbility(GameState state, PlayerAction action) {
        UseAbilityAction abilityAction = parseAndValidateAbilityAction(state, action);
        AbilityRules.Result ability = abilityRules.resolve(state, abilityAction);
        GameState nextState = state.withRuleState(state.inventory(), ability.playerVitals(), ability.combatEncounter(),
                ability.abilityState()).advanceTurn();
        List<GameResult.StateChange> stateChanges = new ArrayList<>(ability.stateChanges());
        stateChanges.add(new GameResult.TurnAdvanced(state.turnNumber(), nextState.turnNumber()));
        List<GameResult.GameEvent> events = new ArrayList<>();
        events.add(GameResult.GameEvent.ACTION_RESOLVED);
        events.add(GameResult.GameEvent.COMBAT_ACTION_RESOLVED);
        events.add(GameResult.GameEvent.ABILITY_RESOLVED);
        if (ability.stateChanges().stream().anyMatch(GameResult.CombatEncounterChanged.class::isInstance)) {
            events.add(GameResult.GameEvent.COMBAT_ENCOUNTER_CHANGED);
        }
        AbilityResult r = ability.abilityResult();
        String cue = "능력 결과는 서버 확정값을 그대로 따른다: ability=%s, target=%s, mp=%d->%d, effect=%s, amount=%d, cooldown=%d.".formatted(
                r.definitionId(), r.targetId(), r.mpBefore(), r.mpAfter(), r.effectType(), r.appliedAmount(), r.cooldownTurns());
        GameResult result = new GameResult(action, GameResult.Outcome.RESOLVED, List.of(), events, stateChanges,
                List.of(action.displayText(), cue));
        return new TurnResolution(result, new StateTransition(state, nextState));
    }

    private CombatRules.Result reconcilePlayerVitals(CombatEncounter encounter, CharacterVitals nextVitals) {
        if (CombatRules.shouldResolve(nextVitals, encounter.enemies())) return CombatRules.resolve(encounter, nextVitals);
        if (CombatEncounter.PLAYER_ACTOR_ID.equals(encounter.currentActorId()) && nextVitals.incapacitated()) return CombatRules.advanceActor(encounter, nextVitals);
        return null;
    }

    private List<VitalsCommand> withTurnEndTiming(List<VitalsCommand> commands) {
        List<VitalsCommand> result = new ArrayList<>();
        if (commands != null) {
            for (VitalsCommand command : commands) {
                Objects.requireNonNull(command, "vitals command는 null일 수 없습니다.");
                if (command instanceof VitalsCommand.AdvanceStatusDurations) throw new IllegalArgumentException("status duration timing은 ActionResolver가 소유합니다.");
                result.add(command);
            }
        }
        result.add(new VitalsCommand.AdvanceStatusDurations(StatusExpiryTrigger.END_OF_TURN));
        return List.copyOf(result);
    }

    private void validateBase(GameState state, PlayerAction action) {
        Objects.requireNonNull(state, "GameState는 필수입니다.");
        Objects.requireNonNull(action, "PlayerAction은 필수입니다.");
        if (action.sourceTurn() != state.turnNumber()) throw new IllegalArgumentException("PlayerAction source turn이 현재 GameState와 일치하지 않습니다.");
        if (action.type() != ActionType.NARRATIVE_CHOICE && action.type() != ActionType.SKILL_CHECK && !isCombatAction(action.type())) {
            throw new IllegalArgumentException("지원하지 않는 action type입니다: " + action.type());
        }
        CombatEncounter encounter = state.combatEncounter();
        if (encounter != null && encounter.active() && !isCombatAction(action.type())) throw new IllegalArgumentException("활성 combat encounter에서는 combat action만 실행할 수 있습니다.");
        if (isCombatAction(action.type())) validateActivePlayerTurn(state);
    }

    private void validateActionArguments(GameState state, PlayerAction action) {
        if (action.type() == ActionType.NARRATIVE_CHOICE) validateNarrativeChoice(action);
        else if (isCombatAction(action.type())) validateCombatAction(state, action);
    }

    private void validateActivePlayerTurn(GameState state) {
        CombatEncounter encounter = state.combatEncounter();
        if (encounter == null || !encounter.active()) throw new IllegalArgumentException("활성 combat encounter가 필요합니다.");
        if (!CombatEncounter.PLAYER_ACTOR_ID.equals(encounter.currentActorId())) throw new IllegalArgumentException("현재 combat actor가 player가 아닙니다.");
        if (state.playerCharacter().vitals().incapacitated()) throw new IllegalArgumentException("행동 불가능한 player는 combat action을 실행할 수 없습니다.");
    }

    private void validateCombatAction(GameState state, PlayerAction action) {
        if (action.type() == ActionType.COMBAT_ATTACK) { parseAndValidateAttackAction(state, action); return; }
        if (action.type() == ActionType.COMBAT_ABILITY) { parseAndValidateAbilityAction(state, action); return; }
        Map<String, String> arguments = action.arguments();
        CombatEncounter encounter = state.combatEncounter();
        if (arguments.size() != 1 || encounter == null || !encounter.encounterId().equals(arguments.get("encounterId"))) {
            throw new IllegalArgumentException("combat action arguments가 현재 encounter와 일치하지 않습니다.");
        }
    }

    private AttackAction parseAndValidateAttackAction(GameState state, PlayerAction action) {
        AttackAction attackAction = AttackAction.from(action);
        CombatEncounter encounter = state.combatEncounter();
        if (encounter == null || !encounter.encounterId().equals(attackAction.encounterId())) throw new IllegalArgumentException("AttackAction encounterId가 현재 encounter와 일치하지 않습니다.");
        EnemyState target = encounter.requireEnemy(attackAction.targetEnemyId());
        if (target.defeated()) throw new IllegalArgumentException("defeated enemy는 공격 대상으로 선택할 수 없습니다.");
        return attackAction;
    }

    private UseAbilityAction parseAndValidateAbilityAction(GameState state, PlayerAction action) {
        UseAbilityAction abilityAction = UseAbilityAction.from(action);
        CombatEncounter encounter = state.combatEncounter();
        if (encounter == null || !encounter.encounterId().equals(abilityAction.encounterId())) {
            throw new IllegalArgumentException("UseAbilityAction encounterId가 현재 encounter와 일치하지 않습니다.");
        }
        return abilityAction;
    }

    private boolean isCombatAction(ActionType type) {
        return type == ActionType.COMBAT_ATTACK || type == ActionType.COMBAT_ABILITY
                || type == ActionType.COMBAT_PASS || type == ActionType.COMBAT_ESCAPE;
    }

    private void validateNarrativeChoice(PlayerAction action) {
        Map<String, String> arguments = action.arguments();
        String choiceId = arguments.get("choiceId");
        if (arguments.size() != 1 || !Integer.toString(action.legacyChoiceId()).equals(choiceId)) throw new IllegalArgumentException("NARRATIVE_CHOICE arguments가 action과 일치하지 않습니다.");
    }

    private SkillCheck parseSkillCheck(PlayerAction action) {
        Map<String, String> arguments = action.arguments();
        if (arguments.size() != 4 || !Integer.toString(action.legacyChoiceId()).equals(arguments.get("choiceId"))) throw new IllegalArgumentException("SKILL_CHECK arguments가 action과 일치하지 않습니다.");
        try {
            StatType statType = StatType.valueOf(arguments.get("statType"));
            int dc = Integer.parseInt(arguments.get("dc"));
            int situationalModifier = Integer.parseInt(arguments.get("situationalModifier"));
            return new SkillCheck(statType, new Difficulty(dc), situationalModifier);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("SKILL_CHECK arguments가 올바르지 않습니다.", exception);
        }
    }

    private void validateSkillCheckResult(GameState state, SkillCheck skillCheck, SkillCheckResult result) {
        if (result.statType() != skillCheck.statType() || result.dc() != skillCheck.difficulty().dc()
                || result.situationalModifier() != skillCheck.situationalModifier()) throw new IllegalArgumentException("저장된 Skill Check 결과가 action 규칙과 일치하지 않습니다.");
        int expectedStatModifier = state.playerCharacter().stats().modifier(skillCheck.statType());
        if (result.statModifier() != expectedStatModifier) throw new IllegalArgumentException("저장된 Skill Check 결과가 현재 canonical 능력치와 일치하지 않습니다.");
    }
}
