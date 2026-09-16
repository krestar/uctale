package com.uctale.uctale.domain.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record StoryMemory(
        List<CanonicalFact> canonicalFacts,
        StorySummary rollingSummary,
        List<GameTurn> recentTurns
) {
    public StoryMemory {
        canonicalFacts = canonicalFacts == null ? List.of() : List.copyOf(canonicalFacts);
        rollingSummary = rollingSummary == null ? StorySummary.empty() : rollingSummary;
        recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
        validateFacts(canonicalFacts);
    }

    public static StoryMemory initial(String worldSetting, String characterSetting, String openingStory) {
        return new StoryMemory(List.of(), StorySummary.empty(), List.of(GameTurn.opening(openingStory)));
    }

    public StoryMemory append(GameTurn turn) {
        List<GameTurn> turns = new ArrayList<>(recentTurns);
        turns.add(turn);
        return new StoryMemory(canonicalFacts, rollingSummary, turns);
    }

    public StoryMemory withCanonicalFact(CanonicalFact fact) {
        if (fact == null) throw new IllegalArgumentException("canonical fact는 필수입니다.");
        if (StoryMemoryFactPolicy.isStateOwned(fact.key())) {
            throw new IllegalArgumentException("GameState가 소유하는 사실은 StoryMemory canonical fact로 저장할 수 없습니다: " + fact.key());
        }
        List<CanonicalFact> next = new ArrayList<>(canonicalFacts.size() + 1);
        for (CanonicalFact existing : canonicalFacts) {
            if (existing.key().equals(fact.key()) && existing.status() == CanonicalFactStatus.ACTIVE) {
                if (fact.sourceTurn() <= existing.sourceTurn()) {
                    throw new IllegalArgumentException("canonical fact 갱신 sourceTurn은 기존 ACTIVE fact보다 이후여야 합니다: " + fact.key());
                }
                next.add(existing.supersede());
            } else {
                next.add(existing);
            }
        }
        next.add(fact);
        return new StoryMemory(next, rollingSummary, recentTurns);
    }

    public StoryMemory compact(StorySummary summary) {
        if (summary == null || summary.emptySummary()) {
            throw new IllegalArgumentException("적용할 summary는 비어 있을 수 없습니다.");
        }
        if (!rollingSummary.emptySummary() && summary.sourceFromTurn() != rollingSummary.sourceFromTurn()) {
            throw new IllegalArgumentException("summary 갱신은 기존 source 시작점을 보존해야 합니다.");
        }
        boolean sourceEndExists = recentTurns.stream()
                .anyMatch(turn -> turn.turnNumber() == summary.sourceToTurn());
        if (!sourceEndExists) {
            throw new IllegalArgumentException("summary source 종료 turn은 현재 recent turn에 존재해야 합니다.");
        }
        List<GameTurn> remaining = recentTurns.stream()
                .filter(turn -> turn.turnNumber() > summary.sourceToTurn())
                .toList();
        if (remaining.size() == recentTurns.size()) {
            throw new IllegalArgumentException("summary가 새 recent turn을 포함해야 합니다.");
        }
        return new StoryMemory(canonicalFacts, summary, remaining);
    }

    public List<CanonicalFact> activeNarrativeFacts() {
        Map<String, CanonicalFact> latest = new LinkedHashMap<>();
        for (CanonicalFact fact : canonicalFacts) {
            if (fact.status() == CanonicalFactStatus.ACTIVE && !StoryMemoryFactPolicy.isStateOwned(fact.key())) {
                CanonicalFact previous = latest.get(fact.key());
                if (previous == null || fact.sourceTurn() > previous.sourceTurn()) {
                    latest.put(fact.key(), fact);
                }
            }
        }
        return List.copyOf(latest.values());
    }

    private static void validateFacts(List<CanonicalFact> facts) {
        Map<String, Integer> activeCounts = new LinkedHashMap<>();
        for (CanonicalFact fact : facts) {
            if (fact == null) throw new IllegalArgumentException("canonical fact는 null일 수 없습니다.");
            if (StoryMemoryFactPolicy.isStateOwned(fact.key())) {
                throw new IllegalArgumentException("GameState가 소유하는 사실은 StoryMemory에 저장할 수 없습니다: " + fact.key());
            }
            if (fact.status() == CanonicalFactStatus.ACTIVE) {
                int count = activeCounts.merge(fact.key(), 1, Integer::sum);
                if (count > 1) {
                    throw new IllegalArgumentException("같은 canonical fact key는 하나만 ACTIVE일 수 있습니다: " + fact.key());
                }
            }
        }
    }
}
