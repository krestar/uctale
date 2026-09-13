# Combat Encounter / EnemyState 경계

## 목적

전투 참가자와 lifecycle뿐 아니라 공격·명중·방어·피해·enemy HP, ability 비용·효과·cooldown 결과까지 canonical state로 고정한다. 전투의 시작·참가·이탈·종료, 현재 actor, enemy 생존 상태와 전투 판정은 서버 규칙이 결정하며 narrative provider는 이를 서술만 한다.

## canonical 모델

`CombatEncounter`는 `PENDING`, `ACTIVE`, `RESOLVED`, `ESCAPED` lifecycle을 가진다. 참가자는 단일 player와 하나 이상의 `EnemyState`다. `EnemyState`는 기존 `CharacterVitals`를 사용하므로 HP/MP/status invariant를 공유하고, `EnemyCombatProfile`의 defense score와 damage reduction을 가진다.

- `PENDING`: 참가자 구성을 변경할 수 있으나 turn order/current actor가 없다.
- `ACTIVE`: 참가자 집합을 고정하고 deterministic turn order/current actor를 가진다.
- `RESOLVED`: player HP가 0이거나 모든 enemy HP가 0인 서버 종료 조건을 만족한 상태다.
- `ESCAPED`: player turn에 서버가 검증한 escape action으로 종료된 상태다.

최소 initiative 정책은 `player`를 먼저 두고 enemy ID를 사전식 정렬한다. 복잡한 initiative roll과 AI 전투 의사결정은 이번 범위가 아니다.

## Attack / Damage / Defense 규칙

`COMBAT_ATTACK`은 `encounterId`와 `targetEnemyId`를 가진 typed `AttackAction`으로 해석한다. active encounter의 player turn이고 target이 존재하며 defeated 상태가 아닐 때만 판정한다.

현재 최소 ruleset은 다음과 같다.

- attack stat: `MIGHT`
- attack roll: `d20 + MIGHT modifier + equipped item attack bonus`
- hit: attack total이 target `defenseScore` 이상
- damage roll: 명중 시에만 `d6 + MIGHT modifier + equipped item damage bonus`
- raw damage는 0 미만으로 내려가지 않는다.
- mitigation 이후 damage: `max(0, raw damage - damageReduction)`
- final damage: target current HP를 초과하지 않도록 clamp
- HP가 0이 되면 `defeated`는 서버 상태에서 파생한다.

`AttackResult`는 raw roll, stat/equipment modifier, defense, mitigation, final damage와 HP before/after를 모두 보존한다. 저장된 판정은 canonical state의 target/HP/modifier와 다시 검증한 뒤 적용한다.

## Ability / Resource / Cooldown 규칙

`COMBAT_ABILITY`는 `encounterId`, `abilityDefinitionId`, `targetId`를 가진 typed `UseAbilityAction`으로 해석한다. 서버 fixture definition이 MP 비용, target rule, damage/heal/status effect와 cooldown을 결정하며 provider prose는 이 값을 입력으로 사용하지 않는다.

ability 사용 시 현재 cooldown과 MP, target을 먼저 검증하고 기존 cooldown의 turn-end 감소를 처리한 뒤 비용과 효과를 적용한다. 방금 사용한 ability의 새 cooldown은 마지막에 설정하므로 같은 사용 턴에서 즉시 감소하지 않는다. 세부 규칙은 [ability-cooldown.md](./ability-cooldown.md)를 기준으로 한다.

## 불변식

- active encounter 없이 `COMBAT_ATTACK`, `COMBAT_ABILITY`, `COMBAT_PASS`, `COMBAT_ESCAPE`를 resolve할 수 없다.
- active encounter에서는 일반 narrative/skill-check action으로 combat 경계를 우회하지 않는다.
- current actor가 player가 아니거나 player가 incapacitated면 player combat action을 거절한다.
- 존재하지 않거나 defeated인 enemy는 attack/ENEMY ability target이 될 수 없다.
- defeated enemy는 current actor가 될 수 없고, 명시적인 서버 부활 규칙 없이 non-defeated 상태로 되돌릴 수 없다.
- participant join/leave는 pending에서만 허용한다.
- `ENEMY_UPDATED` audit은 동일 참가자 집합에서 정확히 한 enemy만 변경할 수 있다.
- attack 또는 ability 결과와 HP/MP/status/cooldown 변경은 같은 `TurnResolution`/`GameTurnCommit`에 포함되어 원자적으로 저장한다.
- combat/vitals 변화는 최종 `GameState`에서 함께 검증해 HP 0인데 encounter가 ACTIVE인 중간 canonical 상태를 저장하지 않는다.

## server-issued available actions

`CombatActionIssuer`는 active player turn에만 token이 있는 `AvailableAction`을 발급한다.

- `COMBAT_ATTACK`: 각 non-defeated enemy별 target action
- `COMBAT_ABILITY`: 현재 MP/cooldown/effect precondition을 만족하는 ability와 target 조합
- `COMBAT_PASS`: 다음 행동 가능한 actor로 전진
- `COMBAT_ESCAPE`: encounter를 `ESCAPED`로 종료

## Persistence / recovery

snapshot schema v7은 v6의 item `combatModifiers`와 enemy `combatProfile`에 더해 `abilityState.cooldowns`를 명시적으로 저장한다. 기존 v5 snapshot은 item modifier를 0/0, enemy profile을 defense 10 / damage reduction 0으로 deterministic upgrade하고, v6 snapshot은 ability 의미가 없으므로 empty cooldown map으로만 v7에 승격한다. 현재 v7에서 필수 전투/ability 필드가 누락된 손상 snapshot은 조용히 기본값 처리하지 않는다.

`game_turn_reservation.attack_result_request_id`와 `attack_result_json`은 최초 서버 Attack 판정과 그 판정을 만든 mutation request를 lease 소유권 아래 저장한다. 동일 idempotency request가 provider 실패 후 재시도되면 새 roll을 만들지 않고 이 판정을 재사용한다. 반대로 다른 mutation request가 만료 reservation을 takeover하면 이전 request의 판정을 재사용하지 않고 새 판정을 확정한다.

combat 변화, `AttackResolved`, `AbilityResolved`, `AbilityCooldownChanged`는 `game_log.combat_changes_json`에 typed audit으로 저장한다. `GameTurnCommit`은 previous encounter/ability state에서 audit을 replay한 결과가 next state와 정확히 일치하는지 검증하고, 기존 cooldown이 성공 turn마다 정확히 한 번 감소했는지도 확인한다. snapshot이 없으면 `GameStateRecovery`가 inventory, player vitals, combat, cooldown audit을 순서대로 replay해 canonical state를 복구한다.

## Narrative provider 경계

`NarrativeContext`는 `AttackResolved`/`AbilityResolved`/`AbilityCooldownChanged` state change, 서버 확정 narrative cue, combat state와 cooldown projection을 provider에 전달한다. provider는 roll, 명중/빗나감, 피해량, ability 비용·효과·target·cooldown, HP/MP/status, enemy 생성·제거·부활이나 encounter lifecycle을 새 canonical 사실로 만들거나 재판정할 수 없다.

## 후속 범위

치명타/속성 상성, AoE, 전술 좌표, 다수 party member, boss phase, 복잡한 AI, 대형 skill tree와 최종 ability balance는 별도 범위다.
