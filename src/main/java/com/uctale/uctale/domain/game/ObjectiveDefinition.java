package com.uctale.uctale.domain.game;

import java.util.Objects;
import java.util.regex.Pattern;

public record ObjectiveDefinition(
        String objectiveId,
        ObjectiveProgressType progressType,
        ObjectiveTrigger trigger,
        String targetKey,
        String targetValue,
        int requiredCount
) {
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    public ObjectiveDefinition {
        if (objectiveId == null || !ID_PATTERN.matcher(objectiveId).matches()) throw new IllegalArgumentException("objectiveId가 올바르지 않습니다.");
        Objects.requireNonNull(progressType, "objective progressType은 필수입니다.");
        Objects.requireNonNull(trigger, "objective trigger는 필수입니다.");
        if (targetKey == null || targetKey.isBlank()) throw new IllegalArgumentException("objective targetKey는 비어 있을 수 없습니다.");
        targetKey = targetKey.trim();
        if (progressType == ObjectiveProgressType.COUNT) {
            if (trigger != ObjectiveTrigger.COLLECTION || requiredCount < 1 || targetValue != null) {
                throw new IllegalArgumentException("COUNT objective definition이 올바르지 않습니다.");
            }
        } else if (progressType == ObjectiveProgressType.BOOLEAN) {
            if (trigger != ObjectiveTrigger.DIALOGUE || requiredCount != 1 || targetValue == null || targetValue.isBlank()) {
                throw new IllegalArgumentException("BOOLEAN objective definition이 올바르지 않습니다.");
            }
            targetValue = targetValue.trim();
        } else {
            if (trigger != ObjectiveTrigger.COMBAT || requiredCount != 1 || targetValue == null || targetValue.isBlank()) {
                throw new IllegalArgumentException("STATE_MATCH objective definition이 올바르지 않습니다.");
            }
            targetValue = targetValue.trim();
        }
    }

    public ObjectiveProgress initialProgress() {
        return switch (progressType) {
            case COUNT -> ObjectiveProgress.count(0);
            case BOOLEAN -> ObjectiveProgress.bool(false);
            case STATE_MATCH -> ObjectiveProgress.state("NONE");
        };
    }

    public boolean completed(ObjectiveProgress progress) {
        Objects.requireNonNull(progress, "objective progress는 필수입니다.");
        if (progress.type() != progressType) throw new IllegalArgumentException("objective progress type이 definition과 일치하지 않습니다.");
        return switch (progressType) {
            case COUNT -> progress.count() >= requiredCount;
            case BOOLEAN -> progress.value();
            case STATE_MATCH -> targetValue.equals(progress.state());
        };
    }
}
