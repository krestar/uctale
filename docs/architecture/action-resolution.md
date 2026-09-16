# Action Resolution / Turn Resolution 경계

## 목적

UCTale의 결정적 게임 규칙은 Narrative provider가 아니라 서버가 소유한다. `/progress`는 서버가 발급한 `PlayerAction`을 먼저 규칙으로 해석하고 canonical state transition을 확정한 뒤 Narrative provider를 호출한다.

현재 action type은 `NARRATIVE_CHOICE`, `SKILL_CHECK`, `COMBAT_ATTACK`, `COMBAT_ABILITY`, `COMBAT_PASS`, `COMBAT_ESCAPE`다. 범용 Rule Engine을 만들지 않고 inventory/equipment, HP/MP/status, combat/ability, quest/objective/flag, NPC relationship/affinity를 작은 타입 안전 규칙으로 유지한다.

## 책임

### `ActionResolver`

순수 domain operation으로 현재 `GameState`, 검증된 `PlayerAction`, 필요한 서버 확정 roll/command를 받아 `GameResult`와 `StateTransition`을 만든다.

- `NARRATIVE_CHOICE` 자체는 임의 성공/실패를 만들지 않는다.
- `SKILL_CHECK`와 `COMBAT_ATTACK`은 reservation 경계에서 보존된 서버 roll을 검증한다.
- `COMBAT_ABILITY`는 definition/target/MP/cooldown을 검증하고 비용·효과·cooldown을 같은 transition에서 확정한다.
- inventory/vitals command가 있는 서버 경로도 typed state change를 생성한다.

active combat에서는 일반 narrative/skill-check로 전투 경계를 우회할 수 없다. 모든 combat action은 active encounter, player actor, 행동 가능 상태, `encounterId`를 검증한다.

### `TurnProcessor`

application canonical 진입점이다. `ActionResolver`의 기본/Skill Check/Attack/inventory/effect resolution을 받은 뒤 `QuestRules.apply`를 적용한다. 명시적인 `RelationshipCommand`가 있는 경로는 이어서 `RelationshipRules.apply`를 적용해 관계 결과도 provider 호출 전에 확정한다.

Quest 규칙과 relationship 규칙은 provider prose를 읽지 않고 이미 확정된 action/state changes와 서버 command만 사용한다.

- collection objective: inventory acquire/quantity 증가 audit
- dialogue objective: typed `NARRATIVE_CHOICE.choiceId`
- combat objective: canonical `CombatEncounterChanged` status
- relationship delta: stable NPC identity + `TALK`/`QUEST`/`GAME_RESULT` reason + source turn/key

따라서 quest progress/flag와 NPC affinity/stage는 Narrative provider 호출 전에 `GameResult`와 next `GameState`에 포함된다. Quest 규칙은 [quest-objective-flags.md](./quest-objective-flags.md), 관계 규칙은 [npc-relationship-affinity.md](./npc-relationship-affinity.md)를 기준으로 한다.

### `CombatActionIssuer`

active player turn에서만 token이 포함된 `AvailableAction`을 발급한다. attack/ability/pass/escape action은 resolver에서 다시 검증하므로 stale/tampered action이 canonical 상태를 변경할 수 없다.

### `GameService`

1. mutation/idempotency와 reservation 획득
2. current session/turn/canonical state 로드
3. server-issued action 검증
4. `TurnProcessor`로 `GameResult`와 canonical `StateTransition` 확정
5. `NarrativeContext` 구성
6. provider attempt/rate limit/budget 확인
7. Narrative provider 호출 및 응답 검증
8. 확정 transition에 narrative transcript 부착
9. `GameTurnCommit`으로 원자 persistence

`GameService`는 개별 inventory/vitals/combat/ability/quest/relationship 공식에 의존하지 않는다.

## Persistence

`GameTurnCommit`은 문자열에서 상태를 계산하지 않는다. previous state에 typed audit을 replay해 next state와 일치하는지 저장 전에 검증한다.

- inventory audit → inventory equality
- vitals/status audit → player vitals equality
- combat audit → encounter equality
- ability cooldown audit → ability state equality
- quest/objective/flag audit → `QuestState` equality
- relationship audit → `RelationshipState` equality
- reservation owner / expected turn / optimistic lock / unique turn constraint

Ability audit은 `game_log.combat_changes_json`, quest/objective/flag audit은 `game_log.quest_changes_json`, NPC relationship audit은 `game_log.relationship_changes_json`에 저장한다. `GameStateRecovery`는 snapshot이 없을 때 모든 typed ledger audit을 순서대로 replay한다.

## Narrative provider 전후 경계

Provider 호출 전에 resolved action, 판정 결과, inventory/vitals/combat/ability 변화, quest/objective/flag 변화, relationship 변화와 canonical next state가 확정된다. `NarrativeContext`는 이 상태를 read-only projection하며 `PlayerAction.token`은 제외한다.

provider prose는 HP/MP/status/target/cooldown, quest 상태/objective progress, World/Event Flag, NPC affinity/stage를 만들거나 변경하는 canonical 근거가 아니다. NPC narrative memory는 public/private 영역을 구조적으로 분리한다. Story transcript도 이미 확정된 rule state를 변경하지 않는다.

## Transaction / retry 경계

Narrative/Image provider 네트워크 호출 동안 DB transaction을 열지 않는다. provider 성공 후 commit 전 crash window에서는 provider 재호출이 가능하다는 bounded at-least-once 정책을 유지하지만, 동일 canonical turn은 optimistic locking/idempotency/stale-owner fencing으로 두 번 적용하지 않는다.

관계 delta는 추가로 NPC별 최신 source turn의 source-key ledger로 같은 사건의 재적용을 막는다. 같은 turn의 서로 다른 사건은 각각 적용되며 동일 source key를 다른 reason/delta로 재사용하면 거절한다.

## 호환성

- 기존 action wire contract를 불필요하게 변경하지 않는다.
- snapshot schema v9은 `abilityState`, `questState`, `relationshipState`를 명시적으로 저장한다.
- v6→v7은 empty ability state, v7→v8은 empty quest/flag state, v8→v9은 empty relationship state만 deterministic하게 추가한다.
- 기존 `WorldState.flags`나 story prose에서 typed quest/relationship 의미를 추측하지 않는다.
- legacy `game_log.quest_changes_json = NULL`, `relationship_changes_json = NULL`은 해당 canonical 변화가 없었던 turn으로 읽는다.
