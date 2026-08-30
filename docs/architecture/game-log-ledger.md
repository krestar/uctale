# GameLog committed-turn ledger

## 책임

UCTale의 persistence는 `GameLog`와 `GameStateSnapshot`을 서로 다른 목적으로 사용합니다.

- `GameLog`: 이미 commit된 canonical turn의 append-only 감사 원장
- `GameStateSnapshot`: 최신 `GameState`를 빠르게 복구하기 위한 materialized snapshot

`GameLog`는 request attempt, `PROCESSING`, `FAILED`, lease 같은 실행 상태를 저장하지 않습니다. 해당 책임은 M2의 idempotency/reservation 이슈(#29, #30)가 별도 request record로 소유합니다.

## 한 turn record의 의미

Opening을 제외한 새 `GameLog` 한 행은 해당 turn을 만든 입력과 결과를 자기 행 안에 함께 저장합니다.

- `turn_number`
- `input_choice_id`
- `input_choice_text`
- `previous_state_version`
- `state_version`
- `story_text`
- `choices_json`
- `image_url`
- `created_at` (`committedAt`)

Opening은 외부 입력이 없으므로 `input_choice_id`, `input_choice_text`가 `null`이고 state transition은 `0 -> 1`입니다.

현재 ruleset에서 state version은 canonical `GameState.turnNumber`와 동일합니다. 이 값은 snapshot schema/ruleset version(#31)과 다른 개념입니다. `state_version`은 **어느 canonical turn state를 이 행이 commit했는지** 식별하고, snapshot version은 **JSON 형식과 ruleset evolution**을 식별합니다.

## append-only 경계

기존 구조는 turn N을 진행할 때 turn N의 `user_choice`를 뒤늦게 수정했습니다. V6 이후 application은 다음 순서를 사용합니다.

1. 현재 turn과 `GameState`를 읽는다.
2. 선택지 ID를 검증하고 표시 텍스트를 확정한다.
3. Narrative 결과를 검증한다.
4. application/domain에서 다음 `GameState`를 확정한다.
5. `GameTurnCommit`으로 입력, 이전/다음 state, narrative 결과를 persistence에 전달한다.
6. persistence는 현재 session/log state version과 commit을 검증하고 새 `GameLog` 한 행과 snapshot/session을 원자적으로 저장한다.

Persistence는 `userChoice + storyText`를 받아 `GameState.advance(...)`를 호출하지 않습니다. 상태 전이를 계산하는 책임은 persistence 바깥에 있습니다.

Commit된 기존 `GameLog`를 수정하는 domain method도 제공하지 않습니다.

## legacy V1~V5 migration

V1~V5에서는 선택 입력이 **선택이 발생한 행이 아니라 그 이전 행의 `user_choice`**에 저장되어 있었습니다. V6 migration은 이를 다음 committed turn 행의 `input_choice_text`로 이동합니다.

과거 schema에는 실제 `choiceId`가 저장되지 않았습니다. `choices_json`과 선택 텍스트를 조합해 추정할 수 있는 경우도 있지만, 중복 문구·과거 데이터 손상 가능성을 고려하면 migration이 ID를 만들어내는 것은 canonical history를 조작할 위험이 있습니다. 따라서:

- legacy `input_choice_text`: 정확히 이관
- legacy `input_choice_id`: `null` = historical unknown
- V6 이후 새 non-opening commit: application contract에서 ID와 text를 모두 필수 기록

`previous_state_version`과 `state_version`은 기존 turn number에서 결정적으로 backfill합니다.

## 복구

Snapshot이 존재하면 최신 상태 복구는 snapshot을 우선 사용합니다.

Snapshot이 없다면 `GameLog`를 turn 순서로 읽고 각 **행 자체의** `input_choice_text`, story, state version을 사용해 현재 `GameState`를 복구합니다. 더 이상 이전 행을 수정해 둔 `user_choice`에 의존하지 않습니다.

전체 event replay engine이나 Event Sourcing은 이 설계의 목표가 아닙니다.
