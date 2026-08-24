package com.uctale.uctale.application.narrative;

import com.uctale.uctale.domain.game.CanonicalFact;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.GameTurn;

import java.util.List;

public record NarrativeContext(
        String worldPremise,
        String playerDescription,
        List<CanonicalFact> canonicalFacts,
        String rollingSummary,
        List<GameTurn> recentTurns,
        String playerAction
) {
    public NarrativeContext {
        canonicalFacts = canonicalFacts == null ? List.of() : List.copyOf(canonicalFacts);
        rollingSummary = rollingSummary == null ? "" : rollingSummary;
        recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
        playerAction = playerAction == null ? "" : playerAction;
    }

    public static NarrativeContext from(GameState state, String playerAction) {
        return new NarrativeContext(
                state.worldState().premise(),
                state.playerCharacter().description(),
                state.storyMemory().canonicalFacts(),
                state.storyMemory().rollingSummary(),
                state.storyMemory().recentTurns(),
                playerAction
        );
    }
}
