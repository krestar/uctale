package com.uctale.uctale.domain.game;

import java.util.ArrayList;
import java.util.List;

public record StoryMemory(
        List<CanonicalFact> canonicalFacts,
        String rollingSummary,
        List<GameTurn> recentTurns
) {
    public static final int RECENT_TURN_LIMIT = 6;
    private static final int SUMMARY_LIMIT = 4000;

    public StoryMemory {
        canonicalFacts = canonicalFacts == null ? List.of() : List.copyOf(canonicalFacts);
        rollingSummary = rollingSummary == null ? "" : rollingSummary.trim();
        recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
    }

    public static StoryMemory initial(String worldSetting, String characterSetting, String openingStory) {
        return new StoryMemory(
                List.of(
                        new CanonicalFact("world.premise", worldSetting),
                        new CanonicalFact("player.description", characterSetting)
                ),
                "",
                List.of(GameTurn.opening(openingStory))
        );
    }

    public StoryMemory append(GameTurn turn) {
        List<GameTurn> turns = new ArrayList<>(recentTurns);
        turns.add(turn);
        String summary = rollingSummary;

        while (turns.size() > RECENT_TURN_LIMIT) {
            GameTurn removed = turns.remove(0);
            summary = appendSummary(summary, removed);
        }
        return new StoryMemory(canonicalFacts, summary, turns);
    }

    private static String appendSummary(String current, GameTurn turn) {
        String entry = "T%d: %s%s".formatted(
                turn.turnNumber(),
                turn.playerAction().isBlank() ? "" : turn.playerAction() + " -> ",
                turn.storyText()
        );
        String combined = current.isBlank() ? entry : current + "\n" + entry;
        if (combined.length() <= SUMMARY_LIMIT) {
            return combined;
        }
        return combined.substring(combined.length() - SUMMARY_LIMIT);
    }
}
