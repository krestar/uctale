# Action Resolution / Turn Resolution 경계

## 목적

UCTale의 결정적 게임 규칙은 Narrative provider가 아니라 서버가 소유한다. `/progress`는 서버가 발급한 `PlayerAction`을 먼저 규칙으로 해석하고, 그 결과와 canonical state transition을 확정한 뒤 Narrative provider를 호출한다.

현재 실제 action type은 `NARRATIVE_CHOICE` 하나이므로 resolver registry나 범용 Rule Engine은 만들지 않는다.

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
5. `GameResult`와 canonical next state에서 provider-safe `NarrativeContext` 구성
6. rate limit / provider attempt accounting 확인
7. Narrative provider 호출
8. provider 응답 검증
9. 확정된 transition에 narrative transcript를 부착하고 다음 server-issued actions 구성
10. `GameTurnCommit`으로 원자적 persistence 호출

`GameService`는 action type별 규칙 공식이나 상태 변경 세부 구현을 알지 않는다.

### Persistence

`GameTurnCommit`은 문자열에서 상태를 계산하지 않고 `StateTransition`을 소유한다. `GamePersistenceService.saveNextTurn()`은 저장 직전에 다음을 재검증한 뒤 한 transaction에서 commit한다.

- reservation owner
- expected turn
- previous/next state version
- DB의 canonical previous state와 transition의 previous state equality
- optimistic lock / unique turn constraint
- canonical result / generated story linkage pair

Persistence는 user choice text나 story prose를 파싱해 규칙 결과를 계산하지 않는다.

## Narrative provider 전후 경계

### Provider 호출 전에 확정되는 것

- resolved `PlayerAction`
- `GameResult.outcome`
- canonical facts/events
- 규칙이 만드는 state changes
- canonical next turn/state transition
- narrative cues
- provider-safe `NarrativeContext`

외부 provider 실패나 다른 prose가 위 값을 다시 판정하지 않는다. `PlayerAction.token`은 provider context에서 제외한다.

### Provider 호출 뒤 허용되는 것

현재 `StoryMemory.recentTurns`는 기존 장기 narrative 호환을 위해 provider가 만든 story prose를 transcript로 보관한다. 이 단계는 이미 확정된 turn number, player/world state, canonical facts, `GameResult`를 변경하지 않는다.

`game_log`에는 새 progress turn부터 `canonical_result_id`와 `generated_story_id`를 함께 기록해 서버 결과와 최종 채택 prose의 연결을 남긴다. legacy/opening 행은 두 값이 모두 비어 있을 수 있다.

Story Memory projection 자체의 전면 재설계와 token budget 개선은 #46 범위다. 세부 NarrativeContext 계약은 [narrative-context.md](./narrative-context.md)를 기준으로 한다.

## Transaction boundary

Narrative/Image provider 네트워크 호출 동안 DB transaction을 열지 않는다.

- load/reservation 검증: 짧은 DB transaction
- action resolution/context projection: pure in-memory operation
- Narrative/Image provider: DB transaction 밖
- final commit: `GameTurnCommit`을 단일 persistence transaction으로 저장

provider 호출 성공 후 commit 전 crash window에서 외부 provider가 재호출될 수 있다는 bounded at-least-once 정책과 stale-owner fencing은 변경하지 않는다. 동일 idempotent mutation retry는 동일 canonical result link를 재구성하고, 완료된 mutation은 provider 없이 committed turn을 replay한다.

## 호환성

- API wire contract와 기존 `NARRATIVE_CHOICE` 선택 흐름은 변경하지 않는다.
- `GameState.advance(playerAction, storyText)`는 legacy log recovery와 기존 테스트 호환을 위해 남기되, 내부적으로 `advanceTurn()` + `recordNarrativeTurn()`을 사용한다.
- 기존 `game_log` 행은 신규 narrative linkage 컬럼이 `NULL`이어도 읽을 수 있다.
- Skill Check 실제 turn 연결과 roll 감사 저장은 #37 범위다.
