# Action Resolution / Turn Resolution 경계

## 목적

UCTale의 결정적 게임 규칙은 Narrative provider가 아니라 서버가 소유한다. `/progress`는 서버가 발급한 `PlayerAction`을 먼저 규칙으로 해석하고, 그 결과와 canonical state transition을 확정한 뒤 Narrative provider를 호출한다.

현재 실제 action type은 `NARRATIVE_CHOICE`, `SKILL_CHECK`, `COMBAT_ATTACK`, `COMBAT_ABILITY`, `COMBAT_PASS`, `COMBAT_ESCAPE`다. 규칙 규모가 작으므로 범용 Rule Engine은 만들지 않는다. Inventory/equipment, HP/MP/status effect, ability cooldown은 provider 출력이 아니라 서버 규칙이 같은 resolution 단계에서 확정한다.

## 책임

### `ActionResolver`

순수 domain operation이다.

입력:

- 현재 `GameState`
- 검증된 `PlayerAction`
- Skill Check action이면 서버가 reservation 경계에서 확정한 `SkillCheckResult`
- Attack action이면 서버가 reservation 경계에서 확정한 `AttackResult`
- 필요할 경우 서버가 확정한 `InventoryCommand`, `VitalsCommand`

출력:

- `TurnResolution`
  - `GameResult`
  - `StateTransition`

`NARRATIVE_CHOICE` 자체는 별도 성공/실패를 만들지 않는다. `SKILL_CHECK`는 보존된 판정 결과를 검증한다. `COMBAT_ATTACK`은 저장된 roll을 검증하고 피해를 확정한다. `COMBAT_ABILITY`는 definition/target/MP/cooldown을 검증한 뒤 비용, 효과, cooldown을 하나의 deterministic transition으로 적용한다.

모든 성공적으로 완료된 turn에서 기존 ability cooldown은 turn-end timing으로 감소한다. Ability 사용 턴에서는 기존 cooldown 감소를 먼저 처리하고 새 cooldown을 마지막에 설정하므로 방금 사용한 ability가 같은 턴에 즉시 감소하지 않는다.

active combat에서는 일반 narrative/skill-check action으로 전투 경계를 우회할 수 없다. 모든 combat action은 active encounter, player current actor, player 행동 가능 상태와 `encounterId`를 검증한다.

### `CombatActionIssuer`

active player turn에서만 token이 포함된 `AvailableAction`을 서버가 발급한다.

- `COMBAT_ATTACK`: non-defeated enemy별 target action
- `COMBAT_ABILITY`: 현재 MP/cooldown/effect precondition을 만족하는 ability/target 조합
- `COMBAT_PASS`: 다음 행동 가능한 actor로 이동
- `COMBAT_ESCAPE`: encounter를 `ESCAPED`로 종료

provider가 combat action type, ability definition, target 또는 canonical 상태를 직접 만들지 않는다. stale/tampered action은 resolver에서 다시 검증한다.

### `TurnProcessor`

application 단계에서 `ActionResolver` 호출과 resolved transition의 narrative transcript 완성을 연결한다. Skill Check와 Attack 난수/decision persistence는 reservation owner 경계에서 수행한다. Ability는 난수 없이 canonical state와 fixture definition으로 결정적이므로 별도 provider decision persistence가 필요하지 않으며 기존 idempotent commit/fencing 경계를 그대로 사용한다.

### `GameService`

다음 순서만 조정한다.

1. mutation/idempotency와 reservation 획득
2. 현재 session/turn/canonical state 로드
3. 서버 발급 action 검증
4. `TurnProcessor.resolve()`로 `GameResult`와 canonical `StateTransition` 확정
5. `GameResult`와 canonical next state에서 provider-safe `NarrativeContext` 구성
6. rate limit / provider attempt accounting 확인
7. Narrative provider 호출
8. provider 응답 검증
9. 확정된 transition에 narrative transcript를 부착하고 다음 server-issued actions 구성
10. `GameTurnCommit`으로 원자적 persistence 호출

`GameService`는 action type별 규칙 공식이나 inventory/vitals/combat/ability 상태 변경 세부 구현을 알지 않는다.

### Persistence

`GameTurnCommit`은 문자열에서 상태를 계산하지 않고 `StateTransition`을 소유한다. 저장 직전에 previous state와 typed audit을 replay해 다음 canonical state와 일치하는지 검증한다.

- inventory audit과 next inventory equality
- vitals/status audit과 next vitals equality
- combat audit과 next encounter equality
- ability cooldown audit과 next ability state equality
- reservation owner / expected turn / optimistic lock / unique turn constraint

Attack의 최초 판정은 reservation에 보존된다. Ability는 deterministic transition이며 `AbilityResolved`, `AbilityCooldownChanged`가 기존 `game_log.combat_changes_json`에 저장된다. `GameStateRecovery`는 snapshot이 없을 때 vitals/combat/cooldown audit을 함께 replay한다.

## Narrative provider 전후 경계

Provider 호출 전에 resolved action, 판정 결과, ability 비용/효과/cooldown, inventory/vitals/combat 변화, canonical next state와 narrative cue가 모두 확정된다. `NarrativeContext`는 이 값만 projection하며 `PlayerAction.token`은 제외한다. provider prose는 확정된 MP/HP/status/target/cooldown을 바꾸거나 새 canonical 결과를 만들 수 없다.

Story transcript는 이미 확정된 rule state를 변경하지 않는다. 자세한 provider 계약은 [narrative-context.md](./narrative-context.md), Inventory 규칙은 [inventory-equipment.md](./inventory-equipment.md), combat 규칙은 [combat-encounter.md](./combat-encounter.md), ability 규칙은 [ability-cooldown.md](./ability-cooldown.md)를 기준으로 한다.

## Transaction boundary

Narrative/Image provider 네트워크 호출 동안 DB transaction을 열지 않는다.

- load/reservation 검증: 짧은 DB transaction
- action resolution/context projection: pure in-memory operation
- deterministic Skill Check/Attack decision persistence: 짧은 reservation transaction
- Narrative/Image provider: DB transaction 밖
- final commit: `GameTurnCommit`을 단일 persistence transaction으로 저장

provider 호출 성공 후 commit 전 crash window에서 외부 provider가 재호출될 수 있다는 bounded at-least-once 정책과 stale-owner fencing은 변경하지 않는다. 동일 idempotent mutation retry는 완료된 canonical commit을 다시 적용하지 않는다.

## 호환성

- API wire contract와 기존 `NARRATIVE_CHOICE` / `SKILL_CHECK` 선택 흐름은 변경하지 않는다.
- `COMBAT_ATTACK`과 `COMBAT_ABILITY`는 server-issued contract다.
- snapshot schema v7은 `abilityState.cooldowns`를 명시적으로 저장하며 v6는 empty cooldown으로만 deterministic upgrade한다.
- 기존 `game_log`의 `combat_changes_json = NULL`은 ability 변화가 없었던 legacy turn으로 읽는다.
- `GameState.advance(playerAction, storyText)`는 legacy log recovery와 기존 테스트 호환을 위해 유지한다.
