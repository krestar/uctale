package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ActionResolver {
    private final InventoryRules inventoryRules = new InventoryRules();

    public boolean requiresSkillCheck(PlayerAction action) {
        Objects.requireNonNull(action, "PlayerAction은 필수입니다.");
        return action.type() == ActionType.SKILL_CHECK;
    }

    public SkillCheckResult rollSkillCheck(GameState state, PlayerAction action, RandomSource randomSource) {
        validateBase(state, action);
        if (action.type() != ActionType.SKILL_CHECK) throw new IllegalArgumentException("Skill Check가 필요하지 않은 action입니다.");
        return parseSkillCheck(action).resolve(state.playerCharacter().stats(), randomSource);
    }

    public TurnResolution resolve(GameState state, PlayerAction action) { return resolveWithInventory(state, action, List.of()); }

    public TurnResolution resolveWithInventory(GameState state, PlayerAction action, List<InventoryCommand> inventoryCommands) {
        validateBase(state, action);
        if (action.type() == ActionType.SKILL_CHECK) throw new IllegalArgumentException("SKILL_CHECK action에는 서버가 확정한 SkillCheckResult가 필요합니다.");
        validateNarrativeChoice(action);
        return resolved(state, action, null, inventoryCommands);
    }

    public TurnResolution resolve(GameState state, PlayerAction action, SkillCheckResult skillCheckResult) {
        return resolveWithInventory(state, action, skillCheckResult, List.of());
    }

    public TurnResolution resolveWithInventory(GameState state, PlayerAction action, SkillCheckResult skillCheckResult,
                                               List<InventoryCommand> inventoryCommands) {
        validateBase(state, action);
        if (action.type() != ActionType.SKILL_CHECK) {
            if (skillCheckResult != null) throw new IllegalArgumentException("Skill Check가 아닌 action에 판정 결과를 연결할 수 없습니다.");
            validateNarrativeChoice(action);
            return resolved(state, action, null, inventoryCommands);
        }
        Objects.requireNonNull(skillCheckResult, "SkillCheckResult는 필수입니다.");
        SkillCheck skillCheck = parseSkillCheck(action);
        validateSkillCheckResult(state, skillCheck, skillCheckResult);
        return resolved(state, action, skillCheckResult, inventoryCommands);
    }

    private TurnResolution resolved(GameState state, PlayerAction action, SkillCheckResult skillCheckResult,
                                    List<InventoryCommand> inventoryCommands) {
        InventoryRules.Result inventoryResult = inventoryRules.apply(state.inventory(), inventoryCommands);
        GameState nextState = state.withInventory(inventoryResult.inventory()).advanceTurn();
        List<GameResult.GameEvent> events = new ArrayList<>();
        events.add(GameResult.GameEvent.ACTION_RESOLVED);
        if (skillCheckResult != null) events.add(GameResult.GameEvent.SKILL_CHECK_RESOLVED);
        List<GameResult.StateChange> changes = new ArrayList<>(inventoryResult.stateChanges());
        changes.add(new GameResult.TurnAdvanced(state.turnNumber(), nextState.turnNumber()));
        GameResult result = new GameResult(action, GameResult.Outcome.RESOLVED, skillCheckResult, List.of(), events, changes,
                List.of(action.displayText()));
        return new TurnResolution(result, new StateTransition(state, nextState));
    }

    private void validateBase(GameState state, PlayerAction action) {
        Objects.requireNonNull(state, "GameState는 필수입니다.");
        Objects.requireNonNull(action, "PlayerAction은 필수입니다.");
        if (action.sourceTurn() != state.turnNumber()) throw new IllegalArgumentException("PlayerAction source turn이 현재 GameState와 일치하지 않습니다.");
        if (action.type() != ActionType.NARRATIVE_CHOICE && action.type() != ActionType.SKILL_CHECK) throw new IllegalArgumentException("지원하지 않는 action type입니다: " + action.type());
    }
    private void validateNarrativeChoice(PlayerAction action) {
        Map<String,String> arguments = action.arguments();
        if (arguments.size() != 1 || !Integer.toString(action.legacyChoiceId()).equals(arguments.get("choiceId"))) throw new IllegalArgumentException("NARRATIVE_CHOICE arguments가 action과 일치하지 않습니다.");
    }
    private SkillCheck parseSkillCheck(PlayerAction action) {
        Map<String,String> arguments = action.arguments();
        if (arguments.size() != 4 || !Integer.toString(action.legacyChoiceId()).equals(arguments.get("choiceId"))) throw new IllegalArgumentException("SKILL_CHECK arguments가 action과 일치하지 않습니다.");
        try {
            return new SkillCheck(StatType.valueOf(arguments.get("statType")), new Difficulty(Integer.parseInt(arguments.get("dc"))), Integer.parseInt(arguments.get("situationalModifier")));
        } catch (RuntimeException exception) { throw new IllegalArgumentException("SKILL_CHECK arguments가 올바르지 않습니다.", exception); }
    }
    private void validateSkillCheckResult(GameState state, SkillCheck skillCheck, SkillCheckResult result) {
        if (result.statType() != skillCheck.statType() || result.dc() != skillCheck.difficulty().dc() || result.situationalModifier() != skillCheck.situationalModifier()) throw new IllegalArgumentException("저장된 Skill Check 결과가 action 규칙과 일치하지 않습니다.");
        if (result.statModifier() != state.playerCharacter().stats().modifier(skillCheck.statType())) throw new IllegalArgumentException("저장된 Skill Check 결과가 현재 canonical 능력치와 일치하지 않습니다.");
    }
}
