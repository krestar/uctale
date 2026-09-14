package com.uctale.uctale.domain.game;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record QuestState(
        Map<String, QuestRuntimeState> quests,
        Map<String, WorldFlag> worldFlags,
        Map<String, EventFlag> eventFlags
) {
    public QuestState {
        quests = immutableQuests(quests);
        worldFlags = immutableWorldFlags(worldFlags);
        eventFlags = immutableEventFlags(eventFlags);
        for (String key : worldFlags.keySet()) {
            if (eventFlags.containsKey(key)) throw new IllegalArgumentException("WORLD/EVENT flag key가 충돌합니다: " + key);
        }
    }

    public static QuestState empty() {
        return new QuestState(Map.of(), Map.of(), Map.of());
    }

    public QuestState addQuest(QuestRuntimeState quest) {
        Objects.requireNonNull(quest, "quest는 필수입니다.");
        if (quests.containsKey(quest.definitionId())) throw new IllegalArgumentException("quest가 이미 존재합니다: " + quest.definitionId());
        TreeMap<String, QuestRuntimeState> next = new TreeMap<>(quests);
        next.put(quest.definitionId(), quest);
        return new QuestState(next, worldFlags, eventFlags);
    }

    public QuestState replaceQuest(QuestRuntimeState quest) {
        Objects.requireNonNull(quest, "quest는 필수입니다.");
        if (!quests.containsKey(quest.definitionId())) throw new IllegalArgumentException("존재하지 않는 quest입니다: " + quest.definitionId());
        TreeMap<String, QuestRuntimeState> next = new TreeMap<>(quests);
        next.put(quest.definitionId(), quest);
        return new QuestState(next, worldFlags, eventFlags);
    }

    public QuestState putFlag(GameFlag flag) {
        Objects.requireNonNull(flag, "flag는 필수입니다.");
        if (flag.namespace() == FlagNamespace.WORLD) {
            if (eventFlags.containsKey(flag.key())) throw new IllegalArgumentException("EVENT flag와 key가 충돌합니다: " + flag.key());
            WorldFlag world = (WorldFlag) flag;
            WorldFlag previous = worldFlags.get(world.key());
            validateVersion(previous, world);
            TreeMap<String, WorldFlag> next = new TreeMap<>(worldFlags);
            next.put(world.key(), world);
            return new QuestState(quests, next, eventFlags);
        }
        if (worldFlags.containsKey(flag.key())) throw new IllegalArgumentException("WORLD flag와 key가 충돌합니다: " + flag.key());
        EventFlag event = (EventFlag) flag;
        EventFlag previous = eventFlags.get(event.key());
        validateVersion(previous, event);
        TreeMap<String, EventFlag> next = new TreeMap<>(eventFlags);
        next.put(event.key(), event);
        return new QuestState(quests, worldFlags, next);
    }

    public GameFlag flag(FlagNamespace namespace, String key) {
        return namespace == FlagNamespace.WORLD ? worldFlags.get(key) : eventFlags.get(key);
    }

    private static void validateVersion(GameFlag previous, GameFlag next) {
        if (previous == null) {
            if (next.version() != 1) throw new IllegalArgumentException("새 flag version은 1이어야 합니다.");
            return;
        }
        if (previous.version() == Integer.MAX_VALUE || next.version() != previous.version() + 1) {
            throw new IllegalArgumentException("flag version은 정확히 1 증가해야 합니다.");
        }
        if (previous.value().equals(next.value())) {
            throw new IllegalArgumentException("flag value는 실제로 변경되어야 합니다.");
        }
    }

    private static Map<String, QuestRuntimeState> immutableQuests(Map<String, QuestRuntimeState> source) {
        if (source == null || source.isEmpty()) return Map.of();
        TreeMap<String, QuestRuntimeState> copy = new TreeMap<>();
        source.forEach((key, value) -> {
            if (key == null || value == null || !key.equals(value.definitionId())) throw new IllegalArgumentException("quest map key가 올바르지 않습니다.");
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, WorldFlag> immutableWorldFlags(Map<String, WorldFlag> source) {
        if (source == null || source.isEmpty()) return Map.of();
        TreeMap<String, WorldFlag> copy = new TreeMap<>();
        source.forEach((key, value) -> {
            if (key == null || value == null || !key.equals(value.key())) throw new IllegalArgumentException("world flag map key가 올바르지 않습니다.");
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, EventFlag> immutableEventFlags(Map<String, EventFlag> source) {
        if (source == null || source.isEmpty()) return Map.of();
        TreeMap<String, EventFlag> copy = new TreeMap<>();
        source.forEach((key, value) -> {
            if (key == null || value == null || !key.equals(value.key())) throw new IllegalArgumentException("event flag map key가 올바르지 않습니다.");
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }
}
