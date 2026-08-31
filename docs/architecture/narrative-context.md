# GameResult 기반 NarrativeContext

## 목적

Narrative provider는 게임 규칙을 판정하지 않는다. 서버가 `ActionResolver`에서 확정한 `GameResult`와 canonical next state를 provider-safe projection으로 전달하고, provider는 그 결과를 story prose와 다음 choice 후보로 표현한다.

## Context 계약

`NarrativeContext.from(canonicalResultId, TurnResolution)`은 provider 호출 전에 다음을 고정한다.

- `canonicalResultId`: 같은 idempotent mutation request에서 재구성해도 동일한 결과 연결 ID
- resolved action projection: legacy choice ID, action type, source turn, 검증된 arguments, display text
- `GameResult.outcome`
- 이번 결과가 만든 canonical facts/events/state changes
- canonical next-state projection: turn, world premise/flags, player description/stats
- memory projection: 기존 canonical facts, rolling summary, recent turns
- narrative cues
- provider가 수행하면 안 되는 canonical mutation 규칙

`PlayerAction.token`은 서버 발급 capability 정보이므로 Narrative provider projection에 포함하지 않는다.

Story Memory 전면 재설계나 token budget 개선은 #46 범위이며, 이번 경계에서는 기존 memory 구조를 read-only projection으로 유지한다.

## Prompt 책임 분리

Gemini progress prompt는 다음 섹션을 명시적으로 분리한다.

1. 확정 게임 결과 ID
2. resolved action
3. 확정 게임 결과
4. 확정 상태 projection
5. canonical facts / rolling summary / recent turns
6. narrative cues
7. 금지 canonical mutation

provider는 다음을 할 수 있다.

- 확정 결과가 드러나는 story prose 생성
- 다음 선택지 후보 제안
- canonical state가 아닌 선택적 visual assets 묘사

provider는 다음을 할 수 없다.

- 서버가 확정한 outcome 재판정
- 서버가 제공하지 않은 roll/성공/실패 창작
- state changes에 없는 HP, 능력치, 아이템, 레벨, 위치, 생사 변경 확정
- state projection/canonical facts 변경

현재 adapter의 provider-specific structured output 강화와 bounded repair/retry는 #35 범위로 남긴다.

## GameLog narrative linkage

`game_log`에는 새 progress turn부터 다음 nullable 컬럼을 함께 기록한다.

- `canonical_result_id`
- `generated_story_id`

legacy/opening row는 두 값이 모두 `NULL`일 수 있다. DB CHECK constraint와 `GameTurnCommit` 불변식은 두 값 중 하나만 존재하는 부분 linkage를 거부한다.

`canonicalResultId`는 `(session, next turn, mutation request)`로 결정적으로 구성한다. 동일 idempotency key/payload가 provider 실패 후 재시도되면 동일 mutation request ID를 사용하므로 동일 canonical result link를 재구성한다.

`generatedStoryId`는 검증된 provider story마다 새로 발급하며, 최종 canonical commit에 채택된 story ID만 GameLog에 기록한다.

## Provider 실패와 retry 정책

provider 호출 전에 `TurnResolution`과 canonical next state는 이미 결정되어 있지만 DB에는 아직 commit하지 않는다.

- provider 실패: mutation을 failed 처리하고 reservation lease를 만료시키며 GameLog/GameState turn은 진행하지 않는다.
- 같은 idempotent 요청 재시도: unchanged canonical previous state와 같은 action으로 pure resolution을 다시 계산하고 동일 `canonicalResultId`를 재사용한다.
- provider 성공 후 commit 실패/stale owner: stale owner는 canonical commit할 수 없다. 이후 재시도에서 provider가 다시 호출될 수 있다는 bounded at-least-once 정책은 유지한다.
- 완료된 mutation retry: 기존 committed turn을 replay하고 provider를 재호출하지 않는다.

외부 provider strict exactly-once를 보장하지 않는다. canonical DB commit exactly-once, stale-owner fencing, bounded provider attempts 정책을 유지한다.

## Persistence 경계

Persistence는 `GameTurnCommit`이 전달한 typed `StateTransition`과 narrative linkage를 저장할 뿐 user text/story prose를 파싱해 규칙을 계산하지 않는다. Narrative prose는 `StoryMemory` transcript를 완성하지만 이미 확정된 rule state/outcome/state changes를 변경하지 않는다.
