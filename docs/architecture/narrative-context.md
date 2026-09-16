# GameResult 기반 NarrativeContext

## 목적

Narrative provider는 게임 규칙을 판정하지 않는다. 서버가 `ActionResolver`/`TurnProcessor`에서 확정한 `GameResult`와 canonical next state를 provider-safe projection으로 전달하고, provider는 그 결과를 story prose와 다음 choice 후보로 표현한다.

## Context 계약

`NarrativeContext.from(canonicalResultId, TurnResolution)`은 provider 호출 전에 다음을 고정한다.

- `canonicalResultId`: 같은 idempotent mutation request에서 재구성해도 동일한 결과 연결 ID
- resolved action projection: legacy choice ID, action type, source turn, 검증된 arguments, display text
- `GameResult.outcome`
- 이번 결과가 만든 canonical facts/events/state changes
- canonical next-state projection: turn, world premise/legacy flags, player description/stats/vitals, inventory/equipment, ability cooldown map
- quest projection: definition ID, `AVAILABLE/ACTIVE/COMPLETED/FAILED`, typed objective progress
- typed flag projection: `WorldFlag` / `EventFlag`의 key/value/version
- NPC relationship projection: stable definition/instance ID, affinity/stage, public/private narrative memory
- combat projection: encounter ID/lifecycle, participant enemy vitals, defeated/incapacitated, deterministic turn order/current actor
- bounded memory projection: active narrative canonical facts, source metadata를 가진 rolling summary, token budget으로 선택한 recent turns
- narrative cues
- provider가 수행하면 안 되는 canonical mutation 규칙

`PlayerAction.token`은 서버 발급 capability 정보이므로 Narrative provider projection에 포함하지 않는다. inventory/quest/flag/relationship/combat/ability projection은 모두 read-only이며 provider 출력은 이 상태의 입력 근거가 아니다.

Story Memory는 GameState가 소유하는 inventory/HP/quest/relationship/NPC/world-event 사실을 복제하지 않는다. narrative canonical fact는 stable key와 source turn, `ACTIVE/SUPERSEDED` 상태를 가지며 prompt projection에는 active fact만 포함한다. rolling summary는 source turn range와 state version을 포함하고, recent turns는 고정 turn 개수가 아니라 명시적 token estimate budget으로 선택한다. summary가 현재 state보다 미래 version을 가리키면 projection에서 제외한다.

## Prompt 책임 분리

Gemini progress prompt는 확정 결과 ID, resolved action, 서버 확정 결과/state projection, bounded memory projection, narrative cues, 금지 canonical mutation을 분리한다.

provider는 확정 결과가 드러나는 story prose와 다음 선택지 후보를 만들 수 있지만 다음을 할 수 없다.

- 서버가 확정한 outcome 재판정
- 서버가 제공하지 않은 roll/성공/실패 창작
- state changes에 없는 HP, MP, status, ability cooldown, quest/objective, flag, 능력치, 아이템, 레벨, 위치, 생사 변경 확정
- ability state changes에 기록된 비용·효과·target·cooldown 변경 또는 재판정
- quest/objective/flag projection과 state changes를 변경하거나 prose만으로 완료·실패·progress·flag 선언
- relationship projection에 없는 affinity/stage/identity 변경 확정
- combat projection/state changes에 없는 enemy 생성·제거·사망·부활, encounter 시작·종료, current actor 변경 확정
- state projection/canonical facts 변경

combat/ability/quest/flag/relationship projection은 모두 read-only다. 공격·피해, ability 비용·효과·cooldown, quest 진행·flag 변경, NPC affinity/stage는 서버가 typed rule/state change로 확정한 값만 projection한다.

## Story Memory summary 경계

오래된 recent transcript가 summary trigger budget을 넘으면 narrative commit 직전에 best-effort summary 갱신을 시도한다.

- summary source turns 자체도 별도 budget으로 제한한다.
- provider 응답은 `source_from_turn`, `source_to_turn`, `state_version`, `text`, `referenced_canonical_keys` schema를 따라야 한다.
- source range/state version이 요청과 다르거나 text가 비어 있거나 summary budget을 넘으면 거절한다.
- `referenced_canonical_keys`에 GameState 소유 key가 포함되면 canonical state와 모순 가능성이 있는 summary로 간주해 거절한다.
- 최대 2회 bounded retry 후에도 실패하면 기존 `StoryMemory`를 그대로 보존하고 정상 canonical turn은 손상시키지 않는다.
- 완전한 자연어 사실 검증은 수행하지 않는다. 최종 충돌 우선순위는 canonical state가 summary보다 높다.

## Gemini structured output 경계

#35 이후 Gemini `generateContent` 요청은 JSON MIME과 response schema를 함께 사용하고 provider 응답을 adapter 내부에서 다시 검증한다.

- 필수 `title`, `story_text`, `choices` 누락/타입 오류를 기본값으로 숨기지 않는다.
- choice는 1~8개, 양의 정수 ID, unique ID, non-blank text와 저장 한계 길이를 검증한다.
- story/title/visual field 길이와 제어 문자를 검증한다.
- optional `visual_assets`가 없으면 canonical 사실을 추측하지 않고 빈 visual projection으로 취급한다.
- malformed JSON, missing field, duplicate ID 같은 구조 오류는 raw 응답 대신 bounded reason code로 분류한다.
- transport/provider HTTP 오류는 response repair 대상으로 바꾸지 않는다.
- raw provider 응답 전문이나 API key를 로그/URL에 노출하지 않는다.

provider-specific JSON/schema 처리는 `provider/gemini` adapter 경계에 남기고 application/domain은 일반적인 narrative 결과와 failure 의미만 다룬다.

## GameLog narrative linkage와 retry

`game_log`에는 progress turn의 `canonical_result_id`, `generated_story_id`를 함께 기록한다. legacy/opening row는 두 값이 모두 `NULL`일 수 있고 부분 linkage는 거절한다.

provider 호출 전에 `TurnResolution`과 canonical next state가 결정되지만 DB에는 아직 commit하지 않는다. provider 실패 시 canonical turn은 진행하지 않으며, 완료된 mutation retry는 committed turn을 replay하고 provider를 재호출하지 않는다. stale reservation owner는 canonical commit할 수 없다. 외부 provider strict exactly-once는 보장하지 않고 canonical DB commit exactly-once와 bounded provider attempt 정책을 유지한다.

Story Memory summary 호출은 주 narrative 생성과 별도 provider 호출이다. summary 실패는 canonical turn 성공 조건이 아니며, summary provider 호출 횟수와 canonical commit 횟수를 동일시하지 않는다.

## Persistence 경계

Persistence는 `GameTurnCommit`의 typed `StateTransition`과 audit을 저장할 뿐 user text/story prose를 파싱해 규칙을 계산하지 않는다. Narrative prose는 transcript를 완성하지만 이미 확정된 rule state/outcome/state changes를 변경하지 않는다.

- ability 변화는 `AbilityResolved`/`AbilityCooldownChanged`와 cooldown projection으로 노출한다.
- quest/flag 변화는 `ObjectiveProgressChanged`, `QuestStatusChanged`, `FlagChanged`와 canonical quest/flag projection으로 노출한다.
- relationship 변화는 typed relationship audit과 canonical relationship projection으로 노출한다.
- 자세한 quest/flag 규칙은 [quest-objective-flags.md](./quest-objective-flags.md), relationship 규칙은 [npc-relationship-affinity.md](./npc-relationship-affinity.md)를 기준으로 한다.
