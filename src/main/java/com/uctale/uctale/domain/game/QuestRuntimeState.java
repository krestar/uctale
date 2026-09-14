package com.uctale.uctale.domain.game;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record QuestRuntimeState(
        String definitionId,
        QuestStatus status,
        Map<String, ObjectiveProgress> objectiveProgress
) {
    public QuestRuntimeState {
        if (definitionId == null || definitionId.isBlank()) throw new IllegalArgumentException("quest definitionId는 비어 있을 수 없습니다.");
        Objects.requireNonNull(status, "quest status는 필수입니다.");
        Objects.requireNonNull(objectiveProgress, "quest objectiveProgress는 필수입니다.");
        QuestDefinition definition = QuestDefinitions.find(definitionId)
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 quest definitionId입니다: " + definitionId));
        if (!definition.objectives().keySet().equals(objectiveProgress.keySet())) {
            throw new IllegalArgumentException("quest objective progress key가 definition과 일치하지 않습니다.");
        }
        TreeMap<String, ObjectiveProgress> copy = new TreeMap<>();
        objectiveProgress.forEach((id, progress) -> {
            ObjectiveDefinition objective = definition.objectives().get(id);
            if (progress == null || progress.type() != objective.progressType()) {
                throw new IllegalArgumentException("quest objective progress type이 definition과 일치하지 않습니다: " + id);
            }
            if (progress.type() == ObjectiveProgressType.COUNT && progress.count() > objective.requiredCount()) {
                throw new IllegalArgumentException("COUNT objective progress가 requiredCount를 초과할 수 없습니다: " + id);
            }
            copy.put(id, progress);
        });
        objectiveProgress = Collections.unmodifiableMap(copy);
        if (status == QuestStatus.COMPLETED && !allObjectivesCompleted(definition, objectiveProgress)) {
            throw new IllegalArgumentException("COMPLETED quest는 모든 objective가 완료되어야 합니다.");
        }
    }

    public static QuestRuntimeState available(QuestDefinition definition) {
        Objects.requireNonNull(definition, "quest definition은 필수입니다.");
        TreeMap<String, ObjectiveProgress> progress = new TreeMap<>();
        definition.objectives().forEach((id, objective) -> progress.put(id, objective.initialProgress()));
        return new QuestRuntimeState(definition.definitionId(), QuestStatus.AVAILABLE, progress);
    }

    public QuestRuntimeState withStatus(QuestStatus nextStatus) {
        validateStatusTransition(status, nextStatus);
        return new QuestRuntimeState(definitionId, nextStatus, objectiveProgress);
    }

    public QuestRuntimeState withProgress(String objectiveId, ObjectiveProgress nextProgress) {
        if (status != QuestStatus.ACTIVE) throw new IllegalStateException("ACTIVE quest만 objective progress를 변경할 수 있습니다.");
        QuestDefinition definition = QuestDefinitions.find(definitionId).orElseThrow();
        ObjectiveDefinition objective = definition.objectives().get(objectiveId);
        if (objective == null) throw new IllegalArgumentException("알 수 없는 objectiveId입니다: " + objectiveId);
        if (nextProgress == null || nextProgress.type() != objective.progressType()) throw new IllegalArgumentException("objective progress type이 올바르지 않습니다.");
        TreeMap<String, ObjectiveProgress> next = new TreeMap<>(objectiveProgress);
        next.put(objectiveId, nextProgress);
        return new QuestRuntimeState(definitionId, status, next);
    }

    public boolean objectivesCompleted() {
        return allObjectivesCompleted(QuestDefinitions.find(definitionId).orElseThrow(), objectiveProgress);
    }

    static void validateStatusTransition(QuestStatus previous, QuestStatus next) {
        Objects.requireNonNull(previous, "previous quest status는 필수입니다.");
        Objects.requireNonNull(next, "next quest status는 필수입니다.");
        if (previous == next) throw new IllegalArgumentException("quest status는 실제로 변경되어야 합니다.");
        boolean valid = switch (previous) {
            case AVAILABLE -> next == QuestStatus.ACTIVE || next == QuestStatus.FAILED;
            case ACTIVE -> next == QuestStatus.COMPLETED || next == QuestStatus.FAILED;
            case COMPLETED, FAILED -> false;
        };
        if (!valid) throw new IllegalArgumentException("허용되지 않는 quest status transition입니다: " + previous + " -> " + next);
    }

    private static boolean allObjectivesCompleted(QuestDefinition definition, Map<String, ObjectiveProgress> progress) {
        return definition.objectives().entrySet().stream().allMatch(entry -> entry.getValue().completed(progress.get(entry.getKey())));
    }
}
