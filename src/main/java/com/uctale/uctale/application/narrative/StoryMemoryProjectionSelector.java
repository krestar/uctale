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

    private static final int CANONICAL_FACT_OVERHEAD = 32;
    private static final int SUMMARY_OVERHEAD = 48;
    private static final int TURN_OVERHEAD = 24;

    private StoryMemoryProjectionSelector() {
    }

    public static Projection project(GameState state) {
        List<CanonicalFact> facts = selectFacts(state.storyMemory().activeNarrativeFacts(), CANONICAL_FACT_TOKEN_BUDGET);
        StorySummary summary = selectSummary(state.storyMemory().rollingSummary(), state.turnNumber());
        List<RecentTurnProjection> turns = selectRecentTurns(state.storyMemory().recentTurns(), RECENT_TURN_TOKEN_BUDGET);
        int estimated = Math.addExact(Math.addExact(estimateFacts(facts), estimateSummary(summary)), estimateRecent(turns));
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
            total = Math.addExact(total, TURN_OVERHEAD);
        }
        return total;
    }

    public static List<GameTurn> selectSummarySource(List<GameTurn> turns) {
        if (turns == null || turns.isEmpty()) return List.of();
        List<GameTurn> source = new ArrayList<>();
        int remaining = SUMMARY_SOURCE_TOKEN_BUDGET;
        for (int i = 0; i < turns.size() - 1 && remaining > TURN_OVERHEAD; i++) {
            GameTurn turn = turns.get(i);
            int actionCost = estimate(turn.playerAction());
            int fixed = Math.addExact(TURN_OVERHEAD, actionCost);
            if (fixed >= remaining && source.isEmpty()) {
                source.add(new GameTurn(turn.turnNumber(), truncate(turn.playerAction(), Math.max(0, remaining - TURN_OVERHEAD)), ""));
                break;
            }
            int storyBudget = Math.max(0, remaining - fixed);
            String story = truncate(turn.storyText(), storyBudget);
            source.add(new GameTurn(turn.turnNumber(), turn.playerAction(), story));
            remaining -= Math.addExact(fixed, estimate(story));
            if (estimate(story) < estimate(turn.storyText())) break;
        }
        return List.copyOf(source);
    }

    private static List<CanonicalFact> selectFacts(List<CanonicalFact> facts, int budget) {
        List<CanonicalFact> selected = new ArrayList<>();
        int used = 0;
        for (CanonicalFact fact : facts) {
            int cost = Math.addExact(
                    Math.addExact(estimate(fact.key()), estimate(fact.value())),
                    CANONICAL_FACT_OVERHEAD
            );
            if ((long) used + cost > budget) continue;
            selected.add(fact);
            used = Math.addExact(used, cost);
        }
        return List.copyOf(selected);
    }

    private static StorySummary selectSummary(StorySummary summary, int currentStateVersion) {
        if (summary == null || summary.emptySummary() || summary.stateVersion() > currentStateVersion) {
            return StorySummary.empty();
        }
        String bounded = truncate(summary.text(), Math.max(0, SUMMARY_TOKEN_BUDGET - SUMMARY_OVERHEAD));
        if (bounded.isBlank()) return StorySummary.empty();
        return new StorySummary(summary.sourceFromTurn(), summary.sourceToTurn(), summary.stateVersion(), bounded);
    }

    private static List<RecentTurnProjection> selectRecentTurns(List<GameTurn> turns, int budget) {
        if (turns == null || turns.isEmpty() || budget <= TURN_OVERHEAD) return List.of();
        List<RecentTurnProjection> reversed = new ArrayList<>();
        int remaining = budget;
        for (int i = turns.size() - 1; i >= 0 && remaining > TURN_OVERHEAD; i--) {
            GameTurn turn = turns.get(i);
            int actionBudget = Math.min(estimate(turn.playerAction()), Math.max(0, (remaining - TURN_OVERHEAD) / 4));
            String action = truncate(turn.playerAction(), actionBudget);
            int storyBudget = Math.max(0, remaining - TURN_OVERHEAD - estimate(action));
            String story = truncate(turn.storyText(), storyBudget);
            if (action.isBlank() && story.isBlank()) break;
            RecentTurnProjection projected = new RecentTurnProjection(turn.turnNumber(), action, story);
            reversed.add(projected);
            remaining -= Math.addExact(TURN_OVERHEAD, Math.addExact(estimate(action), estimate(story)));
            if (estimate(story) < estimate(turn.storyText()) || estimate(action) < estimate(turn.playerAction())) break;
        }
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    private static int estimateFacts(List<CanonicalFact> facts) {
        int total = 0;
        for (CanonicalFact fact : facts) {
            total = Math.addExact(total, estimate(fact.key()));
            total = Math.addExact(total, estimate(fact.value()));
            total = Math.addExact(total, CANONICAL_FACT_OVERHEAD);
        }
        return total;
    }

    private static int estimateSummary(StorySummary summary) {
        if (summary == null || summary.emptySummary()) return 0;
        return Math.addExact(estimate(summary.text()), SUMMARY_OVERHEAD);
    }

    private static int estimateRecent(List<RecentTurnProjection> turns) {
        int total = 0;
        for (RecentTurnProjection turn : turns) {
            total = Math.addExact(total, estimate(turn.playerAction()));
            total = Math.addExact(total, estimate(turn.storyText()));
            total = Math.addExact(total, TURN_OVERHEAD);
        }
        return total;
    }

    static int estimate(String value) {
        if (value == null || value.isEmpty()) return 0;
        int bytes = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            bytes = Math.addExact(bytes, utf8Bytes(codePoint));
            offset += Character.charCount(codePoint);
        }
        return bytes;
    }

    static String truncate(String value, int budget) {
        if (value == null || budget <= 0) return "";
        if (estimate(value) <= budget) return value;
        StringBuilder bounded = new StringBuilder();
        int used = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            int cost = utf8Bytes(codePoint);
            if ((long) used + cost > budget) break;
            bounded.appendCodePoint(codePoint);
            used += cost;
            offset += Character.charCount(codePoint);
        }
        return bounded.toString();
    }

    private static int utf8Bytes(int codePoint) {
        if (codePoint <= 0x7F) return 1;
        if (codePoint <= 0x7FF) return 2;
        if (codePoint <= 0xFFFF) return 3;
        return 4;
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
