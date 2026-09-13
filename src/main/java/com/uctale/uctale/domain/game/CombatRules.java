package com.uctale.uctale.domain.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class CombatRules {

    private CombatRules() {
    }

    public static Result start(CombatEncounter current, String encounterId, List<EnemyState> enemies) {
        if (current != null && !current.terminal()) {
            throw new IllegalStateException("진행 중인 combat encounter가 이미 존재합니다.");
        }
        CombatEncounter next = CombatEncounter.pending(encounterId, enemies);
        return changed(current, next, CombatChangeReason.STARTED);
    }

    public static Result joinEnemy(CombatEncounter current, EnemyState enemy) {
        requireStatus(current, CombatEncounterStatus.PENDING);
        Objects.requireNonNull(enemy, "enemy는 필수입니다.");
        if (current.enemies().containsKey(enemy.enemyId())) {
            throw new IllegalArgumentException("이미 참가한 enemy입니다: " + enemy.enemyId());
        }
        TreeMap<String, EnemyState> enemies = new TreeMap<>(current.enemies());
        enemies.put(enemy.enemyId(), enemy);
        CombatEncounter next = new CombatEncounter(
                current.encounterId(), CombatEncounterStatus.PENDING, enemies, List.of(), null
        );
        return changed(current, next, CombatChangeReason.PARTICIPANT_JOINED);
    }

    public static Result leaveEnemy(CombatEncounter current, String enemyId) {
        requireStatus(current, CombatEncounterStatus.PENDING);
        current.requireEnemy(enemyId);
        if (current.enemies().size() == 1) {
            throw new IllegalStateException("마지막 enemy는 encounter 시작 전에 제거할 수 없습니다.");
        }
        TreeMap<String, EnemyState> enemies = new TreeMap<>(current.enemies());
        enemies.remove(enemyId);
        CombatEncounter next = new CombatEncounter(
                current.encounterId(), CombatEncounterStatus.PENDING, enemies, List.of(), null
        );
        return changed(current, next, CombatChangeReason.PARTICIPANT_LEFT);
    }

    public static Result activate(CombatEncounter current, CharacterVitals playerVitals) {
        requireStatus(current, CombatEncounterStatus.PENDING);
        Objects.requireNonNull(playerVitals, "player vitals는 필수입니다.");
        if (shouldResolve(playerVitals, current.enemies())) {
            throw new IllegalStateException("이미 종료 조건을 만족한 encounter를 활성화할 수 없습니다.");
        }
        List<String> order = CombatEncounter.deterministicTurnOrder(current.enemies());
        String firstActor = firstActorWhoCanAct(order, current.enemies(), playerVitals, 0);
        if (firstActor == null) {
            throw new IllegalStateException("행동 가능한 combat participant가 없습니다.");
        }
        CombatEncounter next = new CombatEncounter(
                current.encounterId(), CombatEncounterStatus.ACTIVE, current.enemies(), order, firstActor
        );
        return changed(current, next, CombatChangeReason.ACTIVATED);
    }

    public static Result advanceActor(CombatEncounter current, CharacterVitals playerVitals) {
        requireStatus(current, CombatEncounterStatus.ACTIVE);
        Objects.requireNonNull(playerVitals, "player vitals는 필수입니다.");
        if (shouldResolve(playerVitals, current.enemies())) {
            return resolve(current, playerVitals);
        }
        int currentIndex = current.turnOrder().indexOf(current.currentActorId());
        String nextActor = firstActorWhoCanAct(
                current.turnOrder(), current.enemies(), playerVitals, currentIndex + 1
        );
        if (nextActor == null) {
            throw new IllegalStateException("행동 가능한 다음 combat participant가 없습니다.");
        }
        CombatEncounter next = new CombatEncounter(
                current.encounterId(), CombatEncounterStatus.ACTIVE, current.enemies(),
                current.turnOrder(), nextActor
        );
        return changed(current, next, CombatChangeReason.ACTOR_ADVANCED);
    }

    public static Result updateEnemy(CombatEncounter current, EnemyState nextEnemy, CharacterVitals playerVitals) {
        requireStatus(current, CombatEncounterStatus.ACTIVE);
        Objects.requireNonNull(nextEnemy, "next enemy는 필수입니다.");
        Objects.requireNonNull(playerVitals, "player vitals는 필수입니다.");
        EnemyState previousEnemy = current.requireEnemy(nextEnemy.enemyId());
        if (previousEnemy.defeated() && !nextEnemy.defeated()) {
            throw new IllegalStateException("defeated enemy는 명시적인 서버 부활 규칙 없이 되살릴 수 없습니다.");
        }
        if (previousEnemy.equals(nextEnemy)) {
            throw new IllegalArgumentException("enemy update는 실제 상태를 변경해야 합니다.");
        }

        TreeMap<String, EnemyState> enemies = new TreeMap<>(current.enemies());
        enemies.put(nextEnemy.enemyId(), nextEnemy);
        CombatEncounter next;
        if (shouldResolve(playerVitals, enemies)) {
            next = new CombatEncounter(current.encounterId(), CombatEncounterStatus.RESOLVED,
                    enemies, current.turnOrder(), null);
        } else if (current.currentActorId().equals(nextEnemy.enemyId()) && !nextEnemy.canAct()) {
            String nextActor = firstActorWhoCanAct(current.turnOrder(), enemies, playerVitals,
                    current.turnOrder().indexOf(current.currentActorId()) + 1);
            if (nextActor == null) {
                throw new IllegalStateException("행동 가능한 다음 combat participant가 없습니다.");
            }
            next = new CombatEncounter(current.encounterId(), CombatEncounterStatus.ACTIVE,
                    enemies, current.turnOrder(), nextActor);
        } else {
            next = new CombatEncounter(current.encounterId(), CombatEncounterStatus.ACTIVE,
                    enemies, current.turnOrder(), current.currentActorId());
        }
        return changed(current, next, CombatChangeReason.ENEMY_UPDATED);
    }

    public static Result resolve(CombatEncounter current, CharacterVitals playerVitals) {
        requireStatus(current, CombatEncounterStatus.ACTIVE);
        Objects.requireNonNull(playerVitals, "player vitals는 필수입니다.");
        if (!shouldResolve(playerVitals, current.enemies())) {
            throw new IllegalStateException("combat encounter 종료 조건을 만족하지 않았습니다.");
        }
        CombatEncounter next = new CombatEncounter(
                current.encounterId(), CombatEncounterStatus.RESOLVED, current.enemies(), current.turnOrder(), null
        );
        return changed(current, next, CombatChangeReason.RESOLVED);
    }

    public static Result escape(CombatEncounter current, CharacterVitals playerVitals) {
        requireStatus(current, CombatEncounterStatus.ACTIVE);
        Objects.requireNonNull(playerVitals, "player vitals는 필수입니다.");
        if (!CombatEncounter.PLAYER_ACTOR_ID.equals(current.currentActorId())) {
            throw new IllegalStateException("player turn이 아니므로 combat에서 이탈할 수 없습니다.");
        }
        if (playerVitals.incapacitated()) {
            throw new IllegalStateException("행동 불가능한 player는 combat에서 이탈할 수 없습니다.");
        }
        CombatEncounter next = new CombatEncounter(
                current.encounterId(), CombatEncounterStatus.ESCAPED, current.enemies(), current.turnOrder(), null
        );
        return changed(current, next, CombatChangeReason.ESCAPED);
    }

    public static boolean shouldResolve(CharacterVitals playerVitals, Map<String, EnemyState> enemies) {
        Objects.requireNonNull(playerVitals, "player vitals는 필수입니다.");
        Objects.requireNonNull(enemies, "enemies는 필수입니다.");
        if (enemies.isEmpty()) {
            throw new IllegalArgumentException("combat encounter에는 최소 1명의 enemy가 필요합니다.");
        }
        return playerVitals.defeated() || enemies.values().stream().allMatch(EnemyState::defeated);
    }

    public static void validateState(CombatEncounter encounter, CharacterVitals playerVitals) {
        Objects.requireNonNull(encounter, "combat encounter는 필수입니다.");
        Objects.requireNonNull(playerVitals, "player vitals는 필수입니다.");
        if (!encounter.active()) return;
        if (shouldResolve(playerVitals, encounter.enemies())) {
            throw new IllegalArgumentException("종료 조건을 만족한 encounter는 ACTIVE일 수 없습니다.");
        }
        if (CombatEncounter.PLAYER_ACTOR_ID.equals(encounter.currentActorId())) {
            if (playerVitals.incapacitated()) {
                throw new IllegalArgumentException("행동 불가능한 player를 current actor로 둘 수 없습니다.");
            }
            return;
        }
        if (!encounter.requireEnemy(encounter.currentActorId()).canAct()) {
            throw new IllegalArgumentException("행동 불가능한 enemy를 current actor로 둘 수 없습니다.");
        }
    }

    public static CombatEncounter replay(
            CombatEncounter initial,
            List<GameResult.StateChange> stateChanges,
            CharacterVitals resultingPlayerVitals
    ) {
        CombatEncounter current = initial;
        if (stateChanges != null) {
            for (GameResult.StateChange change : stateChanges) {
                if (!(change instanceof GameResult.CombatEncounterChanged combatChange)) continue;
                if (!Objects.equals(current, combatChange.previousEncounter())) {
                    throw new IllegalArgumentException("combat audit의 previous encounter가 현재 상태와 일치하지 않습니다.");
                }
                current = combatChange.nextEncounter();
            }
        }
        if (current != null) validateState(current, resultingPlayerVitals);
        return current;
    }

    static void validateChange(
            CombatEncounter previous,
            CombatEncounter next,
            CombatChangeReason reason
    ) {
        Objects.requireNonNull(next, "next combat encounter는 필수입니다.");
        Objects.requireNonNull(reason, "combat change reason은 필수입니다.");
        if (Objects.equals(previous, next)) {
            throw new IllegalArgumentException("combat state change는 실제 상태를 변경해야 합니다.");
        }
        switch (reason) {
            case STARTED -> {
                if (previous != null && !previous.terminal()) {
                    throw new IllegalArgumentException("진행 중 encounter를 새 encounter로 교체할 수 없습니다.");
                }
                requireNextStatus(next, CombatEncounterStatus.PENDING, reason);
            }
            case PARTICIPANT_JOINED, PARTICIPANT_LEFT -> {
                requirePrevious(previous, reason);
                requirePreviousStatus(previous, CombatEncounterStatus.PENDING, reason);
                requireNextStatus(next, CombatEncounterStatus.PENDING, reason);
                requireSameEncounterId(previous, next, reason);
            }
            case ACTIVATED -> {
                requirePrevious(previous, reason);
                requirePreviousStatus(previous, CombatEncounterStatus.PENDING, reason);
                requireNextStatus(next, CombatEncounterStatus.ACTIVE, reason);
                requireSameEncounterId(previous, next, reason);
            }
            case ACTOR_ADVANCED -> {
                requirePrevious(previous, reason);
                requirePreviousStatus(previous, CombatEncounterStatus.ACTIVE, reason);
                requireNextStatus(next, CombatEncounterStatus.ACTIVE, reason);
                requireSameEncounterId(previous, next, reason);
                if (!previous.enemies().equals(next.enemies()) || previous.currentActorId().equals(next.currentActorId())) {
                    throw new IllegalArgumentException("ACTOR_ADVANCED change가 participant/current actor 규칙과 일치하지 않습니다.");
                }
            }
            case ENEMY_UPDATED -> {
                requirePrevious(previous, reason);
                requirePreviousStatus(previous, CombatEncounterStatus.ACTIVE, reason);
                if (next.status() != CombatEncounterStatus.ACTIVE && next.status() != CombatEncounterStatus.RESOLVED) {
                    throw new IllegalArgumentException("ENEMY_UPDATED 이후 status가 올바르지 않습니다.");
                }
                requireSameEncounterId(previous, next, reason);
            }
            case RESOLVED -> {
                requirePrevious(previous, reason);
                requirePreviousStatus(previous, CombatEncounterStatus.ACTIVE, reason);
                requireNextStatus(next, CombatEncounterStatus.RESOLVED, reason);
                requireSameEncounterId(previous, next, reason);
            }
            case ESCAPED -> {
                requirePrevious(previous, reason);
                requirePreviousStatus(previous, CombatEncounterStatus.ACTIVE, reason);
                requireNextStatus(next, CombatEncounterStatus.ESCAPED, reason);
                requireSameEncounterId(previous, next, reason);
            }
        }
    }

    private static Result changed(CombatEncounter previous, CombatEncounter next, CombatChangeReason reason) {
        return new Result(next, List.of(new GameResult.CombatEncounterChanged(previous, next, reason)));
    }

    private static void requireStatus(CombatEncounter encounter, CombatEncounterStatus expected) {
        if (encounter == null || encounter.status() != expected) {
            throw new IllegalStateException(expected + " combat encounter가 필요합니다.");
        }
    }

    private static String firstActorWhoCanAct(
            List<String> order,
            Map<String, EnemyState> enemies,
            CharacterVitals playerVitals,
            int startIndex
    ) {
        for (int offset = 0; offset < order.size(); offset++) {
            String actor = order.get(Math.floorMod(startIndex + offset, order.size()));
            if (CombatEncounter.PLAYER_ACTOR_ID.equals(actor)) {
                if (!playerVitals.incapacitated()) return actor;
            } else {
                EnemyState enemy = enemies.get(actor);
                if (enemy != null && enemy.canAct()) return actor;
            }
        }
        return null;
    }

    private static void requirePrevious(CombatEncounter previous, CombatChangeReason reason) {
        if (previous == null) throw new IllegalArgumentException(reason + " change에는 previous encounter가 필요합니다.");
    }

    private static void requirePreviousStatus(CombatEncounter previous, CombatEncounterStatus status, CombatChangeReason reason) {
        if (previous.status() != status) throw new IllegalArgumentException(reason + " previous status가 올바르지 않습니다.");
    }

    private static void requireNextStatus(CombatEncounter next, CombatEncounterStatus status, CombatChangeReason reason) {
        if (next.status() != status) throw new IllegalArgumentException(reason + " next status가 올바르지 않습니다.");
    }

    private static void requireSameEncounterId(CombatEncounter previous, CombatEncounter next, CombatChangeReason reason) {
        if (!previous.encounterId().equals(next.encounterId())) {
            throw new IllegalArgumentException(reason + " change에서 encounterId를 변경할 수 없습니다.");
        }
    }

    public record Result(CombatEncounter encounter, List<GameResult.StateChange> stateChanges) {
        public Result {
            Objects.requireNonNull(encounter, "combat encounter는 필수입니다.");
            stateChanges = List.copyOf(stateChanges);
        }
    }
}
