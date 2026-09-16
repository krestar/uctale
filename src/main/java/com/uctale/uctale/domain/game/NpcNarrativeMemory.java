package com.uctale.uctale.domain.game;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public record NpcNarrativeMemory(Map<String, String> publicFacts, Map<String, String> privateFacts) {
    public NpcNarrativeMemory {
        publicFacts = immutableFacts(publicFacts, "public");
        privateFacts = immutableFacts(privateFacts, "private");
        for (String key : publicFacts.keySet()) {
            if (privateFacts.containsKey(key)) throw new IllegalArgumentException("NPC memory fact key가 public/private에 동시에 존재할 수 없습니다: " + key);
        }
    }

    public static NpcNarrativeMemory empty() {
        return new NpcNarrativeMemory(Map.of(), Map.of());
    }

    public NpcNarrativeMemory withPublicFact(String key, String value) {
        return withFact(key, value, true);
    }

    public NpcNarrativeMemory withPrivateFact(String key, String value) {
        return withFact(key, value, false);
    }

    private NpcNarrativeMemory withFact(String key, String value, boolean isPublic) {
        validateFact(key, value);
        if (isPublic && privateFacts.containsKey(key) || !isPublic && publicFacts.containsKey(key)) {
            throw new IllegalArgumentException("NPC memory fact visibility는 같은 key에서 변경할 수 없습니다: " + key);
        }
        TreeMap<String, String> next = new TreeMap<>(isPublic ? publicFacts : privateFacts);
        next.put(key, value);
        return isPublic ? new NpcNarrativeMemory(next, privateFacts) : new NpcNarrativeMemory(publicFacts, next);
    }

    private static Map<String, String> immutableFacts(Map<String, String> source, String visibility) {
        if (source == null || source.isEmpty()) return Map.of();
        TreeMap<String, String> copy = new TreeMap<>();
        source.forEach((key, value) -> {
            try {
                validateFact(key, value);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("NPC " + visibility + " memory가 올바르지 않습니다.", exception);
            }
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static void validateFact(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new IllegalArgumentException("NPC memory key/value는 비어 있을 수 없습니다.");
        }
    }
}
