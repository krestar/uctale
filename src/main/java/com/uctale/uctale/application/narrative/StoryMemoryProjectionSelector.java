package com.uctale.uctale.application.narrative;

import com.uctale.uctale.domain.game.CanonicalFact;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.GameTurn;
import com.uctale.uctale.domain.game.StorySummary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StoryMemoryProjectionSelector {

    public static final int CANONICAL_FACT_TOKEN_BUDGET = 512;
    public static final int SUMMARY_TOKEN_BUDGET = 1_024;
    public static final int RECENT_TURN_TOKEN_BUDGET = 1_600;
    public static final int MEMORY_TOKEN_BUDGET = CANONICAL_FACT_TOKEN_BUDGET + SUMMARY_TOKEN_BUDGET + RECENT_TURN_TOKEN_BUDGET;
    public static final int SUMMARY_SOURCE_TOKEN_BUDGET = 2_400;
    public static final int SUMMARY_TRIGGER_TOKEN_BUDGET = 3_200;

    private StoryMemoryProjectionSelector() {
    }

    public static Projection project(GameState state) {
        List<CanonicalFact> facts = selectFacts(state.storyMemory().activeNarrativeFacts(), CANONICAL_FACT_TOKEN_BUDGET);
        StorySummary summary = selectSummary(state.storyMemory().rollingSummary(), state.turnNumber());
        List<RecentTurnProjection> turns = selectRecentTurns(state.storyMemory().recentTurns(), RECENT_TURN_TOKEN_BUDGET);
        int estimated = estimateFacts(facts) + estimateSummary(summary) + estimateRecent(turns);
        if (estimated > MEMORY_TOKEN_BUDGET) {
            throw new IllegalStateException("StoryMemory projection이 token budget을 초과했습니다: " + estimated);
        }
        return new Projection(facts, summary, turns, estimated);
    }

    public static int estimateRawTurns(List<GameTurn> turns) {
        if (turns == null) return 0;
        int total = 0;
        for (GameTurn turn : turns) {
            total = Math.addExact(total, estimate(turn.playerAction()));
            total = Math.addExact(total, estimate(turn.storyText()));
            total = Math.addExact(total, 8);
        }
        return total;
    }

    public static List<GameTurn> selectSummarySource(List<GameTurn> turns) {
        if (turns == null || turns.isEmpty()) return List.of();
        List<GameTurn> source = new ArrayList<>();
        int remaining = SUMMARY_SOURCE_TOKEN_BUDGET;
        for (int i = 0; i < turns.size() - 1 && remaining > 0; i++) {
            GameTurn turn = turns.get(i);
            int fixed = 8 + estimate(turn.playerAction());
            if (fixed >= remaining && source.isEmpty()) {
                source.add(new GameTurn(turn.turnNumber(), truncate(turn.playerAction(), Math.max(0, remaining - 8)), ""));
                break;
            }
            int storyBudget = Math.max(0, remaining - fixed);
            String story = truncate(turn.storyText(), storyBudget);
            source.add(new GameTurn(turn.turnNumber(), turn.playerAction(), story));
            remaining -= fixed + estimate(story);
            if (estimate(story) < estimate(turn.storyText())) break;
        }
        return List.copyOf(source);
    }

    private static List<CanonicalFact> selectFacts(List<CanonicalFact> facts, int budget) {
        List<CanonicalFact> selected = new ArrayList<>();
        int used = 0;
        for (CanonicalFact fact : facts) {
            int cost = estimate(fact.key()) + estimate(fact.value()) + 8;
            if (used + cost > budget) continue;
            selected.add(fact);
            used += cost;
        }
        return List.copyOf(selected);
    }

    private static StorySummary selectSummary(StorySummary summary, int currentStateVersion) {
        if (summary == null || summary.emptySummary() || summary.stateVersion() > currentStateVersion) {
            return StorySummary.empty();
        }
        String bounded = truncate(summary.text(), SUMMARY_TOKEN_BUDGET);
        if (bounded.isBlank()) return StorySummary.empty();
        return new StorySummary(summary.sourceFromTurn(), summary.sourceToTurn(), summary.stateVersion(), bounded);
    }

    private static List<RecentTurnProjection> selectRecentTurns(List<GameTurn> turns, int budget) {
        if (turns == null || turns.isEmpty() || budget <= 0) return List.of();
        List<RecentTurnProjection> reversed = new ArrayList<>();
        int remaining = budget;
        for (int i = turns.size() - 1; i >= 0 && remaining > 0; i--) {
            GameTurn turn = turns.get(i);
            int fixed = 8;
            if (remaining <= fixed) break;
            int actionBudget = Math.min(estimate(turn.playerAction()), Math.max(0, remaining / 4));
            String action = truncate(turn.playerAction(), actionBudget);
            int storyBudget = Math.max(0, remaining - fixed - estimate(action));
            String story = truncate(turn.storyText(), storyBudget);
            if (action.isBlank() && story.isBlank()) break;
            RecentTurnProjection projected = new RecentTurnProjection(turn.turnNumber(), action, story);
            reversed.add(projected);
            remaining -= fixed + estimate(action) + estimate(story);
            if (estimate(story) < estimate(turn.storyText()) || estimate(action) < estimate(turn.playerAction())) break;
        }
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    private static int estimateFacts(List<CanonicalFact> facts) {
        int total = 0;
        for (CanonicalFact fact : facts) total += estimate(fact.key()) + estimate(fact.value()) + 8;
        return total;
    }

    private static int estimateSummary(StorySummary summary) {
        return summary == null || summary.emptySummary() ? 0 : estimate(summary.text());
    }

    private static int estimateRecent(List<RecentTurnProjection> turns) {
        int total = 0;
        for (RecentTurnProjection turn : turns) total += estimate(turn.playerAction()) + estimate(turn.storyText()) + 8;
        return total;
    }

    static int estimate(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    static String truncate(String value, int budget) {
        if (value == null || budget <= 0) return "";
        int codePoints = estimate(value);
        if (codePoints <= budget) return value;
        int end = value.offsetByCodePoints(0, budget);
        return value.substring(0, end);
    }

    public record RecentTurnProjection(int turnNumber, String playerAction, String storyText) {
        public RecentTurnProjection {
            if (turnNumber < 1) throw new IllegalArgumentException("recent turn number는 1 이상이어야 합니다.");
            playerAction = playerAction == null ? "" : playerAction;
            storyText = storyText == null ? "" : storyText;
        }
    }

    public record Projection(List<CanonicalFact> canonicalFacts, StorySummary rollingSummary,
                             List<RecentTurnProjection> recentTurns, int estimatedTokens) {
        public Projection {
            canonicalFacts = canonicalFacts == null ? List.of() : List.copyOf(canonicalFacts);
            rollingSummary = rollingSummary == null ? StorySummary.empty() : rollingSummary;
            recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
            if (estimatedTokens < 0 || estimatedTokens > MEMORY_TOKEN_BUDGET) {
                throw new IllegalArgumentException("memory projection token estimate가 올바르지 않습니다.");
            }
        }
    }
}
