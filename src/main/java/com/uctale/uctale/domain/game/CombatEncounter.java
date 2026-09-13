package com.uctale.uctale.domain.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record CombatEncounter(
        String encounterId,
        CombatEncounterStatus status,
        Map<String, EnemyState> enemies,
        List<String> turnOrder,
        String currentActorId
) {
    public static final String PLAYER_ACTOR_ID = "player";

    public CombatEncounter {
        if (encounterId == null || encounterId.isBlank()) {
            throw new IllegalArgumentException("encounterId는 비어 있을 수 없습니다.");
        }
        encounterId = encounterId.trim();
        Objects.requireNonNull(status, "combat status는 필수입니다.");
        Objects.requireNonNull(enemies, "combat enemies는 필수입니다.");
        Objects.requireNonNull(turnOrder, "combat turnOrder는 필수입니다.");

        if (enemies.isEmpty()) {
            throw new IllegalArgumentException("combat encounter에는 최소 1명의 enemy가 필요합니다.");
        }
        TreeMap<String, EnemyState> enemyCopy = new TreeMap<>();
        for (Map.Entry<String, EnemyState> entry : enemies.entrySet()) {
            String enemyId = entry.getKey();
            EnemyState enemy = Objects.requireNonNull(entry.getValue(), "enemy는 null일 수 없습니다.");
            if (enemyId == null || !enemyId.equals(enemy.enemyId())) {
                throw new IllegalArgumentException("enemy map key와 enemyId가 일치해야 합니다.");
            }
            if (PLAYER_ACTOR_ID.equals(enemyId)) {
                throw new IllegalArgumentException("enemyId는 player actor ID를 사용할 수 없습니다.");
            }
            enemyCopy.put(enemyId, enemy);
        }
        enemies = Collections.unmodifiableMap(enemyCopy);
        turnOrder = List.copyOf(turnOrder);
        if (new HashSet<>(turnOrder).size() != turnOrder.size()) {
            throw new IllegalArgumentException("combat turnOrder에는 중복 actor가 있을 수 없습니다.");
        }

        List<String> expectedOrder = deterministicTurnOrder(enemies);
        switch (status) {
            case PENDING -> {
                if (!turnOrder.isEmpty() || currentActorId != null) {
                    throw new IllegalArgumentException("PENDING encounter는 turn order/current actor를 가질 수 없습니다.");
                }
                if (enemies.values().stream().anyMatch(EnemyState::defeated)) {
                    throw new IllegalArgumentException("PENDING encounter에 defeated enemy를 참가시킬 수 없습니다.");
                }
            }
            case ACTIVE -> {
                if (!turnOrder.equals(expectedOrder)) {
                    throw new IllegalArgumentException("ACTIVE encounter의 turnOrder가 결정적 순서와 일치하지 않습니다.");
                }
                if (currentActorId == null || !turnOrder.contains(currentActorId)) {
                    throw new IllegalArgumentException("ACTIVE encounter의 current actor가 올바르지 않습니다.");
                }
                EnemyState currentEnemy = enemies.get(currentActorId);
                if (currentEnemy != null && !currentEnemy.canAct()) {
                    throw new IllegalArgumentException("행동 불가능한 enemy를 current actor로 둘 수 없습니다.");
                }
            }
            case RESOLVED, ESCAPED -> {
                if (!turnOrder.equals(expectedOrder) || currentActorId != null) {
                    throw new IllegalArgumentException("종료된 encounter는 결정적 turnOrder를 보존하고 current actor를 비워야 합니다.");
                }
            }
        }
    }

    public static CombatEncounter pending(String encounterId, List<EnemyState> enemies) {
        if (enemies == null || enemies.isEmpty()) {
            throw new IllegalArgumentException("combat encounter에는 최소 1명의 enemy가 필요합니다.");
        }
        TreeMap<String, EnemyState> byId = new TreeMap<>();
        for (EnemyState enemy : enemies) {
            Objects.requireNonNull(enemy, "enemy는 null일 수 없습니다.");
            if (byId.put(enemy.enemyId(), enemy) != null) {
                throw new IllegalArgumentException("중복 enemyId를 사용할 수 없습니다: " + enemy.enemyId());
            }
        }
        return new CombatEncounter(encounterId, CombatEncounterStatus.PENDING, byId, List.of(), null);
    }

    public static List<String> deterministicTurnOrder(Map<String, EnemyState> enemies) {
        List<String> order = new ArrayList<>();
        order.add(PLAYER_ACTOR_ID);
        order.addAll(new TreeMap<>(enemies).keySet());
        return List.copyOf(order);
    }

    public boolean active() {
        return status == CombatEncounterStatus.ACTIVE;
    }

    public boolean terminal() {
        return status == CombatEncounterStatus.RESOLVED || status == CombatEncounterStatus.ESCAPED;
    }

    public EnemyState requireEnemy(String enemyId) {
        if (enemyId == null || enemyId.isBlank()) {
            throw new IllegalArgumentException("enemyId는 비어 있을 수 없습니다.");
        }
        EnemyState enemy = enemies.get(enemyId);
        if (enemy == null) {
            throw new IllegalArgumentException("encounter에 존재하지 않는 enemy입니다: " + enemyId);
        }
        return enemy;
    }
}
