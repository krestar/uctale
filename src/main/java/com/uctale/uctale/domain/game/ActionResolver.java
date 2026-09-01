package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ActionResolver {

    public boolean requiresSkillCheck(PlayerAction action) {
        Objects.requireNonNull(action, "PlayerAction은 필수입니다.");
        return action.type() == ActionType.SKILL_CHECK;
    }

    public SkillCheckResult rollSkillCheck(GameState state, PlayerAction action, RandomSource randomSource) {
        validateBase(state, action);
        if (action.type() != ActionType.SKILL_CHECK) {
            throw new IllegalArgumentException("Skill Check가 필요하지 않은 action입니다.");
        }
        SkillCheck skillCheck = parseSkillCheck(action);
        return skillCheck.resolve(state.playerCharacter().stats(), randomSource);
    }

    public TurnResolution resolve(GameState state, PlayerAction action) {
        validateBase(state, action);
        if (action.type() == ActionType.SKILL_CHECK) {
            throw new IllegalArgumentException("SKILL_CHECK action에는 서버가 확정한 SkillCheckResult가 필요합니다.");
        }
        validateNarrativeChoice(action);
        return resolved(state, action, null);
    }

    public TurnResolution resolve(GameState state, PlayerAction action, SkillCheckResult skillCheckResult) {
        validateBase(state, action);
        if (action.type() != ActionType.SKILL_CHECK) {
            if (skillCheckResult != null) {
                throw new IllegalArgumentException("Skill Check가 아닌 action에 판정 결과를 연결할 수 없습니다.");
            }
            validateNarrativeChoice(action);
            return resolved(state, action, null);
        }
        Objects.requireNonNull(skillCheckResult, "SkillCheckResult는 필수입니다.");
        SkillCheck skillCheck = parseSkillCheck(action);
        validateSkillCheckResult(state, skillCheck, skillCheckResult);
        return resolved(state, action, skillCheckResult);
    }

    private TurnResolution resolved(GameState state, PlayerAction action, SkillCheckResult skillCheckResult) {
        GameState nextState = state.advanceTurn();
        List<GameResult.GameEvent> events = new ArrayList<>();
        events.add(GameResult.GameEvent.ACTION_RESOLVED);
        if (skillCheckResult != null) {
            events.add(GameResult.GameEvent.SKILL_CHECK_RESOLVED);
        }
        GameResult result = new GameResult(
                action,
                GameResult.Outcome.RESOLVED,
                skillCheckResult,
                List.of(),
                events,
                List.of(new GameResult.TurnAdvanced(state.turnNumber(), nextState.turnNumber())),
                List.of(action.displayText())
        );
        return new TurnResolution(result, new StateTransition(state, nextState));
    }

    private void validateBase(GameState state, PlayerAction action) {
        Objects.requireNonNull(state, "GameState는 필수입니다.");
        Objects.requireNonNull(action, "PlayerAction은 필수입니다.");
        if (action.sourceTurn() != state.turnNumber()) {
            throw new IllegalArgumentException("PlayerAction source turn이 현재 GameState와 일치하지 않습니다.");
        }
        if (action.type() != ActionType.NARRATIVE_CHOICE && action.type() != ActionType.SKILL_CHECK) {
            throw new IllegalArgumentException("지원하지 않는 action type입니다: " + action.type());
        }
    }

    private void validateNarrativeChoice(PlayerAction action) {
        Map<String, String> arguments = action.arguments();
        String choiceId = arguments.get("choiceId");
        if (arguments.size() != 1 || !Integer.toString(action.legacyChoiceId()).equals(choiceId)) {
            throw new IllegalArgumentException("NARRATIVE_CHOICE arguments가 action과 일치하지 않습니다.");
        }
    }

    private SkillCheck parseSkillCheck(PlayerAction action) {
        Map<String, String> arguments = action.arguments();
        if (arguments.size() != 4
                || !Integer.toString(action.legacyChoiceId()).equals(arguments.get("choiceId"))) {
            throw new IllegalArgumentException("SKILL_CHECK arguments가 action과 일치하지 않습니다.");
        }
        try {
            StatType statType = StatType.valueOf(arguments.get("statType"));
            int dc = Integer.parseInt(arguments.get("dc"));
            int situationalModifier = Integer.parseInt(arguments.get("situationalModifier"));
            return new SkillCheck(statType, new Difficulty(dc), situationalModifier);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("SKILL_CHECK arguments가 올바르지 않습니다.", exception);
        }
    }

    private void validateSkillCheckResult(
            GameState state,
            SkillCheck skillCheck,
            SkillCheckResult result
    ) {
        if (result.statType() != skillCheck.statType()
                || result.dc() != skillCheck.difficulty().dc()
                || result.situationalModifier() != skillCheck.situationalModifier()) {
            throw new IllegalArgumentException("저장된 Skill Check 결과가 action 규칙과 일치하지 않습니다.");
        }
        int expectedStatModifier = state.playerCharacter().stats().modifier(skillCheck.statType());
        if (result.statModifier() != expectedStatModifier) {
            throw new IllegalArgumentException("저장된 Skill Check 결과가 현재 canonical 능력치와 일치하지 않습니다.");
        }
    }
}
