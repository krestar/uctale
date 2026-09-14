package com.uctale.uctale.domain.game;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

public record QuestDefinition(
        String definitionId,
        Map<String, ObjectiveDefinition> objectives
) {
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    public QuestDefinition {
        if (definitionId == null || !ID_PATTERN.matcher(definitionId).matches()) throw new IllegalArgumentException("quest definitionId가 올바르지 않습니다.");
        Objects.requireNonNull(objectives, "quest objectives는 필수입니다.");
        if (objectives.isEmpty()) throw new IllegalArgumentException("quest는 최소 1개 objective가 필요합니다.");
        TreeMap<String, ObjectiveDefinition> copy = new TreeMap<>();
        for (var entry : objectives.entrySet()) {
            ObjectiveDefinition objective = Objects.requireNonNull(entry.getValue(), "objective definition은 null일 수 없습니다.");
            if (entry.getKey() == null || !entry.getKey().equals(objective.objectiveId())) {
                throw new IllegalArgumentException("objective map key와 objectiveId가 일치해야 합니다.");
            }
            copy.put(entry.getKey(), objective);
        }
        objectives = Collections.unmodifiableMap(copy);
    }
}
