# Action Resolution / Turn Resolution 경계

## 목적

UCTale의 결정적 게임 규칙은 Narrative provider가 아니라 서버가 소유한다. `/progress`는 서버가 발급한 `PlayerAction`을 먼저 규칙으로 해석하고, 그 결과와 canonical state transition을 확정한 뒤 Narrative provider를 호출한다.

현재 #34에서는 첫 규칙 경계만 도입한다. 실제 action type은 `NARRATIVE_CHOICE` 하나이므로 resolver registry나 범용 Rule Engine은 만들지 않는다.

## 책임

### `ActionResolver`

순수 domain operation이다.

입력:

- 현재 `GameState`
- 검증된 `PlayerAction`

출력:

- `TurnResolution`
  - `GameResult`
  - `StateTransition`

현재 `GameResult`는 다음 최소 정보를 가진다.

- 서버가 resolve한 `PlayerAction`
- `Outcome.RESOLVED`
- 이번 action이 만든 canonical facts
- domain events
- typed state changes
- Narrative Engine이 활용할 수 있는 narrative cues

`NARRATIVE_CHOICE`는 아직 성공/실패·HP·아이템 같은 규칙을 만들지 않으므로 새 canonical fact는 없고, `ACTION_RESOLVED` event와 `TurnAdvanced` state change만 생성한다.

### `TurnProcessor`

application 단계에서 `ActionResolver` 호출과 resolved transition의 narrative transcript 완성을 연결한다. 현재 resolver가 하나뿐이고 상태가 없으므로 registry나 Spring bean graph를 추가하지 않는다.

### `GameService`

다음 순서만 조정한다.

1. mutation/idempotency와 reservation 획득
2. 현재 session/turn/canonical state 로드
3. 서버 발급 action 검증
4. `TurnProcessor.resolve()`로 `GameResult`와 canonical `StateTransition` 확정
5. rate limit / provider attempt accounting 확인
6. Narrative provider 호출
7. provider 응답 검증
8. 확정된 transition에 narrative transcript를 부착하고 다음 server-issued actions 구성
9. `GameTurnCommit`으로 원자적 persistence 호출

`GameService`는 action type별 규칙 공식이나 상태 변경 세부 구현을 알지 않는다.

### Persistence

`GameTurnCommit`은 문자열에서 상태를 계산하지 않고 `StateTransition`을 소유한다. `GamePersistenceService.saveNextTurn()`은 저장 직전에 다음을 재검증한 뒤 한 transaction에서 commit한다.

- reservation owner
- expected turn
- previous/next state version
- DB의 canonical previous state와 transition의 previous state equality
- optimistic lock / unique turn constraint

Persistence는 user choice text나 story prose를 파싱해 규칙 결과를 계산하지 않는다.

## Narrative provider 전후 경계

### Provider 호출 전에 확정되는 것

- resolved `PlayerAction`
- `GameResult.outcome`
- canonical facts/events
- 규칙이 만드는 state changes
- canonical next turn/state transition
- narrative cues

외부 provider 실패나 다른 prose가 위 값을 다시 판정하지 않는다.

### Provider 호출 뒤 허용되는 것

현재 `StoryMemory.recentTurns`는 기존 장기 narrative 호환을 위해 provider가 만든 story prose를 transcript로 보관한다. 이 단계는 이미 확정된 turn number, player/world state, canonical facts, `GameResult`를 변경하지 않는다.

즉 #34에서는 **규칙 state transition**과 **narrative-memory transcript 완성**을 타입으로 분리했다. `StoryMemory`가 현재 `GameState` snapshot 안에 함께 저장되는 기존 schema는 유지한다. 이를 제거하거나 `NarrativeContext`를 `GameResult` projection으로 전환하는 작업은 #36/#46의 별도 범위다.

## Transaction boundary

Narrative/Image provider 네트워크 호출 동안 DB transaction을 열지 않는다.

- load/reservation 검증: 짧은 DB transaction
- action resolution: pure in-memory domain operation
- Narrative/Image provider: DB transaction 밖
- final commit: `GameTurnCommit`을 단일 persistence transaction으로 저장

provider 호출 성공 후 commit 전 crash window에서 외부 provider가 재호출될 수 있다는 기존 bounded at-least-once 정책과 stale-owner fencing은 변경하지 않는다.

## 호환성

- API wire contract와 기존 `NARRATIVE_CHOICE` 선택 흐름은 변경하지 않는다.
- `GameState.advance(playerAction, storyText)`는 legacy log recovery와 기존 테스트 호환을 위해 남기되, 내부적으로 `advanceTurn()` + `recordNarrativeTurn()`을 사용한다.
- snapshot schema/DB migration은 변경하지 않는다.
- Skill Check 실제 turn 연결과 roll 감사 저장은 #37 범위다.
