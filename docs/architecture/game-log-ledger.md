# GameLog committed-turn ledger

## 책임

UCTale persistence는 `GameLog`와 `GameStateSnapshot`을 서로 다른 목적으로 사용합니다.

- `GameLog`: 이미 commit된 canonical turn의 append-only 감사 원장
- `GameStateSnapshot`: 최신 `GameState`를 빠르게 복구하기 위한 materialized snapshot

`GameLog`는 request attempt, `PROCESSING`, `FAILED`, lease 같은 실행 상태를 저장하지 않습니다. 현재 이 책임은 `game_mutation_request`와 `game_turn_reservation`이 분리해 소유합니다.

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

현재 ruleset에서 state version은 canonical `GameState.turnNumber`와 동일합니다. 이 값은 snapshot schema/ruleset version과 다른 개념입니다.

## append-only 경계

V6 이후 application은 다음 순서를 사용합니다.

1. 현재 turn과 `GameState`를 읽습니다.
2. 서버 발급 action을 검증하고 canonical 입력 ID/text를 확정합니다.
3. Narrative 결과를 검증합니다.
4. application/domain에서 다음 `GameState`를 구성합니다.
5. `GameTurnCommit`으로 입력, 이전/다음 state, narrative 결과를 persistence에 전달합니다.
6. persistence는 session/log state version, canonical previous state와 reservation owner를 검증하고 새 `GameLog` 한 행과 snapshot/session/request 결과를 원자적으로 저장합니다.

현재 M3는 이 구조 위에서 #34/#36을 통해 **Narrative provider 호출 전에 규칙 결과와 다음 state를 확정**하는 방향으로 경계를 강화합니다.

`GamePersistenceService`는 사용자 문자열이나 story prose를 파싱해 게임 규칙을 계산하지 않습니다. Snapshot이 없을 때의 복구 계산도 `GameStateRecovery`로 분리합니다.

Commit된 기존 `GameLog`를 수정하는 domain method는 제공하지 않으며 entity는 Hibernate `@Immutable`로 dirty update 대상에서도 제외합니다.

## idempotency와 reservation의 관계

- `game_mutation_request`: owner/idempotency key/fingerprint와 PROCESSING/COMPLETED/FAILED 상태를 관리합니다.
- `game_turn_reservation`: `(session_id, expected_turn)` lease, owner fencing, provider attempt 상한을 관리합니다.
- `GameLog`: 위 실행 상태가 아니라 **성공적으로 commit된 canonical history**만 기록합니다.

완료된 idempotency retry는 기존 canonical `GameLog`를 사용해 응답을 replay하고 새 ledger row를 쓰지 않습니다. lease takeover가 발생해도 stale owner는 commit 단계에서 거절되므로 동일 `(session_id, turn_number)` ledger가 중복 생성되지 않습니다.

## legacy V1~V5 migration

V1~V5에서는 선택 입력이 선택이 발생한 행이 아니라 그 이전 행의 `user_choice`에 저장되어 있었습니다. V6 migration은 이를 다음 committed turn 행의 `input_choice_text`로 이동합니다.

과거 schema에는 실제 `choiceId`가 저장되지 않았으므로 migration이 ID를 추정해 canonical history를 만들지 않습니다.

- legacy `input_choice_text`: 정확히 이관
- legacy `input_choice_id`: `null` = historical unknown
- V6 이후 새 non-opening commit: application contract에서 ID와 text를 모두 기록

`previous_state_version`과 `state_version`은 기존 turn number에서 결정적으로 backfill합니다.

기존 `user_choice` DB 컬럼은 rollback compatibility를 위해 남겨두지만 현재 `GameLog` entity는 읽거나 쓰지 않습니다. canonical ledger 의미는 `input_choice_*` 컬럼이 소유합니다.

## 복구

Snapshot이 존재하면 최신 상태 복구는 snapshot을 우선 사용합니다.

Snapshot이 없다면 `GameLog`를 turn 순서로 읽고 각 행의 `input_choice_text`, story, state version을 사용해 `GameStateRecovery`가 현재 `GameState`를 복구합니다. 이전 행의 legacy `user_choice`에 의존하지 않습니다.

전체 event replay engine이나 Event Sourcing은 이 설계의 목표가 아닙니다.
