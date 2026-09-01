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

## Gemini structured output 경계

#35 이후 Gemini `generateContent` 요청은 JSON MIME과 response schema를 함께 사용하고, provider 응답은 adapter 내부에서 다시 검증한다.

- 필수 `title`, `story_text`, `choices`가 누락되거나 잘못된 타입이면 기본값으로 보정하지 않는다.
- choice는 1~8개, 양의 정수 ID, unique ID, non-blank text와 저장 한계에 맞는 길이를 검증한다.
- story/title/visual field의 길이와 제어 문자를 검증한다.
- optional `visual_assets`가 없으면 canonical 사실을 추측하지 않고 빈 visual projection으로 취급한다.
- malformed JSON, missing field, duplicate ID 같은 복구 가능한 구조 오류는 raw 응답 대신 bounded reason code로 분류한다.
- transport/provider HTTP 오류와 같은 hard failure는 response repair 대상으로 바꾸지 않는다.
- 로그에는 raw provider 응답 전문을 남기지 않고 `context`와 bounded reason code만 남긴다.
- Gemini API key는 query string이 아니라 `x-goog-api-key` header로 전달한다.

JSON parsing, Gemini response wrapper, response schema 같은 provider-specific 처리는 `provider/gemini` adapter 경계에 남기고 application/domain은 `NarrativeTurn`과 일반적인 recoverable/hard failure 의미만 다룬다.

## GameLog narrative linkage

`game_log`에는 새 progress turn부터 다음 nullable 컬럼을 함께 기록한다.

- `canonical_result_id`
- `generated_story_id`

legacy/opening row는 두 값이 모두 `NULL`일 수 있다. DB CHECK constraint와 `GameTurnCommit` 불변식은 두 값 중 하나만 존재하는 부분 linkage를 거부한다.

`canonicalResultId`는 `(session, next turn, mutation request)`로 결정적으로 구성한다. 동일 idempotency key/payload가 provider 실패 후 재시도되면 동일 mutation request ID를 사용하므로 동일 canonical result link를 재구성한다.

`generatedStoryId`는 검증된 provider story마다 새로 발급하며, 최종 canonical commit에 채택된 story ID만 GameLog에 기록한다.

## Provider 실패와 retry 정책

provider 호출 전에 `TurnResolution`과 canonical next state는 이미 결정되어 있지만 DB에는 아직 commit하지 않는다.

- Gemini 구조 오류 recovery는 최대 3 provider attempt로 제한한다. 첫 실패 뒤 50ms, 두 번째 실패 뒤 150ms의 bounded backoff를 사용한다.
- recovery prompt에는 실패한 raw 응답을 다시 주입하지 않고 bounded reason code와 동일 요청/확정 결과를 사용한다.
- progress의 첫 provider attempt와 각 recovery attempt는 현재 reservation owner에 대해 `provider_attempt_count`를 다시 검증·증가시킨다. stale owner 또는 attempt 상한에 도달한 요청은 다음 provider 호출 전에 중단된다.
- telemetry는 하나의 logical narrative operation에 실제 retry count와 attempt count를 기록해 비용 ledger가 내부 recovery 횟수를 반영하도록 한다.
- provider/transport hard failure는 추가 response repair 없이 실패 처리한다.
- provider 실패 또는 recovery 소진: mutation을 failed 처리하고 reservation lease를 만료시키며 GameLog/GameState turn은 진행하지 않는다.
- 같은 idempotent 요청 재시도: unchanged canonical previous state와 같은 action으로 pure resolution을 다시 계산하고 동일 `canonicalResultId`를 재사용한다.
- provider 성공 후 commit 실패/stale owner: stale owner는 canonical commit할 수 없다. 이후 재시도에서 provider가 다시 호출될 수 있다는 bounded at-least-once 정책은 유지한다.
- 완료된 mutation retry: 기존 committed turn을 replay하고 provider를 재호출하지 않는다.

외부 provider strict exactly-once를 보장하지 않는다. canonical DB commit exactly-once, stale-owner fencing, bounded provider attempts 정책을 유지한다.

## Persistence 경계

Persistence는 `GameTurnCommit`이 전달한 typed `StateTransition`과 narrative linkage를 저장할 뿐 user text/story prose를 파싱해 규칙을 계산하지 않는다. Narrative prose는 `StoryMemory` transcript를 완성하지만 이미 확정된 rule state/outcome/state changes를 변경하지 않는다.
