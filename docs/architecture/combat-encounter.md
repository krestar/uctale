# Combat Encounter / EnemyState 경계

## 목적

공격·피해 계산보다 먼저 전투 참가자와 lifecycle을 canonical state로 고정한다. 전투의 시작·참가·이탈·종료, 현재 actor, enemy 생존 상태는 서버 규칙이 결정하며 narrative provider는 이를 서술만 한다.

## canonical 모델

`CombatEncounter`는 `PENDING`, `ACTIVE`, `RESOLVED`, `ESCAPED` lifecycle을 가진다. 참가자는 단일 player와 하나 이상의 `EnemyState`다. `EnemyState`는 기존 `CharacterVitals`를 사용하므로 HP/MP/status invariant를 공유한다.

- `PENDING`: 참가자 구성을 변경할 수 있으나 turn order/current actor가 없다.
- `ACTIVE`: 참가자 집합을 고정하고 deterministic turn order/current actor를 가진다.
- `RESOLVED`: player HP가 0이거나 모든 enemy HP가 0인 서버 종료 조건을 만족한 상태다.
- `ESCAPED`: player turn에 서버가 검증한 escape action으로 종료된 상태다.

최소 initiative 정책은 `player`를 먼저 두고 enemy ID를 사전식 정렬한다. 복잡한 initiative roll과 AI 전투 의사결정은 이번 범위가 아니다.

## 불변식

- active encounter 없이 `COMBAT_PASS`, `COMBAT_ESCAPE`를 resolve할 수 없다.
- active encounter에서는 일반 narrative/skill-check action으로 combat 경계를 우회하지 않는다.
- current actor가 player가 아니거나 player가 incapacitated면 player combat action을 거절한다.
- defeated enemy는 current actor가 될 수 없고, 명시적인 서버 부활 규칙 없이 non-defeated 상태로 되돌릴 수 없다.
- participant join/leave는 pending에서만 허용한다.
- `ENEMY_UPDATED` audit은 동일 참가자 집합에서 정확히 한 enemy만 변경할 수 있다.
- combat/vitals 변화는 최종 `GameState`에서 함께 검증해 HP 0인데 encounter가 ACTIVE인 중간 canonical 상태를 저장하지 않는다.

## server-issued available actions

`CombatActionIssuer`는 active player turn에만 token이 있는 `AvailableAction`을 발급한다.

- `COMBAT_PASS`: 다음 행동 가능한 actor로 전진
- `COMBAT_ESCAPE`: encounter를 `ESCAPED`로 종료

공격 action, target validation, attack/damage/defense 계산은 #42 범위다.

## Persistence / recovery

snapshot schema v5는 `combatEncounter`를 명시적으로 저장한다. 기존 v4 세션은 과거 prose에서 전투를 추정하지 않고 `combatEncounter: null`로 upgrade한다.

combat 변화는 `game_log.combat_changes_json`에 typed `CombatEncounterChanged` audit으로 저장한다. `GameTurnCommit`은 previous encounter에서 audit을 replay한 결과가 next encounter와 정확히 일치하는지 검증한다. snapshot이 없으면 `GameStateRecovery`가 inventory, player vitals, combat audit 순으로 replay해 참가자와 current actor를 복구한다.

## Narrative provider 경계

`NarrativeContext.StateProjection`은 combat lifecycle, current actor, turn order와 각 enemy의 vitals/defeated/incapacitated 상태를 read-only projection으로 전달한다. provider는 enemy 생성·제거·사망·부활이나 encounter lifecycle을 새 canonical 사실로 만들 수 없다.

## 후속 범위

#42에서 typed attack action, target validation, attack/defense/damage, enemy HP 변화와 defeated 판정을 이 aggregate 위에 연결한다. 전술 좌표, 다수 party member, boss phase, 복잡한 AI는 별도 범위다.
