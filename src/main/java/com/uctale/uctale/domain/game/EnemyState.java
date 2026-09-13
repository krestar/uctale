package com.uctale.uctale.domain.game;

import java.util.Objects;

public record EnemyState(
        String enemyId,
        String displayName,
        CharacterVitals vitals,
        EnemyCombatProfile combatProfile
) {
    public EnemyState {
        enemyId = requireText(enemyId, "enemyId");
        displayName = requireText(displayName, "enemy displayName");
        Objects.requireNonNull(vitals, "enemy vitals는 필수입니다.");
        Objects.requireNonNull(combatProfile, "enemy combatProfile은 필수입니다.");
    }

    public EnemyState(String enemyId, String displayName, CharacterVitals vitals) {
        this(enemyId, displayName, vitals, EnemyCombatProfile.defaults());
    }

    public EnemyState withVitals(CharacterVitals nextVitals) {
        Objects.requireNonNull(nextVitals, "enemy vitals는 필수입니다.");
        if (defeated() && !nextVitals.defeated()) {
            throw new IllegalStateException("defeated enemy는 명시적인 서버 부활 규칙 없이 되살릴 수 없습니다.");
        }
        return new EnemyState(enemyId, displayName, nextVitals, combatProfile);
    }

    public boolean defeated() {
        return vitals.defeated();
    }

    public boolean incapacitated() {
        return vitals.incapacitated();
    }

    public boolean canAct() {
        return !incapacitated();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 비어 있을 수 없습니다.");
        }
        return value.trim();
    }
}
