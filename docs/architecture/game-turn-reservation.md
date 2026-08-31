# Game Turn Reservation Lease

## Goal

`PROGRESS` 요청은 Narrative provider를 호출하기 전에 `(session_id, expected_turn)` reservation을 획득해야 한다. 활성 lease는 PostgreSQL의 단일 primary key row로 표현하며, 정상 동시 요청에서는 한 요청만 provider 호출 구간에 진입한다.

## Request states

`game_mutation_request`는 idempotency 이력을 보관한다.

- `PROCESSING`: 최초 요청이 처리 중이다. 같은 `Idempotency-Key`의 동시 재요청은 `409 MUTATION_IN_PROGRESS`와 `Retry-After`를 받는다.
- `COMPLETED`: 저장된 canonical 결과를 replay한다.
- `FAILED`: 같은 key/payload로 재시도할 수 있다.

## Turn reservation

`game_turn_reservation`은 turn lease와 bounded provider attempt 수를 함께 보관한다.

- key: `(session_id, expected_turn)`
- owner: `request_id` + 매 lease 획득마다 새로 생성되는 `lease_owner`
- 기본 lease: 90초 (`app.game.turn-reservation.lease-seconds`)
- 최대 provider 시도: 같은 turn reservation row 기준 3회
- `attempt_count`: lease 획득 횟수가 아니라 실제 Narrative provider 실행 경계에 진입한 횟수

서로 다른 idempotency key가 같은 turn을 요청해도 lease가 유효하면 후발 요청은 provider를 호출하지 않고 `409 MUTATION_IN_PROGRESS`를 받는다.

Reservation 획득 자체는 provider attempt를 소비하지 않는다. session/turn 검증, choice/action 검증, rate limit, 전역 provider budget guard처럼 provider 호출 전에 끝나는 실패는 lease만 실패/만료 처리하며 `attempt_count`를 증가시키지 않는다.

모든 pre-provider 검증과 budget guard를 통과한 뒤 Narrative provider를 실제 호출하기 직전에 현재 `request_id`/`lease_owner`가 여전히 유효한지 확인하면서 `attempt_count`를 원자적으로 증가시킨다. 이미 3회에 도달했거나 lease가 만료/회수된 owner는 provider 호출 구간에 진입할 수 없다.

## Transaction boundaries

Reservation 획득과 provider attempt 시작 표시는 각각 짧은 DB transaction에서 끝난다. Gemini 네트워크 호출 동안 DB transaction이나 row lock을 유지하지 않는다.

Provider 응답을 받은 뒤 canonical commit transaction에서 다음을 다시 검증한다.

1. `(session_id, expected_turn)` reservation row가 존재한다.
2. 현재 `lease_owner`가 provider를 실행한 시도의 owner와 동일하다.
3. session `expectedTurn`과 optimistic version이 그대로다.
4. GameLog/state snapshot의 이전 version이 canonical state와 일치한다.

검증 후 canonical state와 GameLog를 한 transaction에서 저장하고 reservation을 해제한다.

## Failure and crash semantics

Provider 호출 전 예외가 발생하면 mutation request를 `FAILED`로 바꾸고 lease를 즉시 만료시키되 provider attempt는 소비하지 않는다.

Provider 실행 경계에 진입한 뒤 provider 또는 commit 전 단계에서 예외가 발생하면 해당 시도는 `attempt_count`에 남고 mutation request를 `FAILED`로 바꾸며 lease를 즉시 만료시켜 남은 횟수 범위에서 재시도를 허용한다.

프로세스가 provider 시작 표시 직후 또는 provider 성공 직후 DB commit 전에 죽으면 실패 처리를 실행할 수 없다. 이 경우 lease가 만료된 뒤 다른 요청이 reservation을 회수할 수 있고 provider가 다시 호출될 수 있다. 따라서 외부 provider 호출은 strict exactly-once가 아니라 **bounded at-least-once**이며, reservation row의 `attempt_count`로 turn당 최대 3번까지 제한한다.

provider 시작 표시와 실제 네트워크 I/O 사이의 프로세스 crash는 보수적으로 provider attempt를 소비한 것으로 본다. 이 경계를 DB transaction과 외부 provider 호출 사이에서 완전히 원자화할 수 없기 때문에, 중복 호출 상한을 지키는 방향으로 fail-safe 한다.

반면 canonical state와 GameLog는 reservation owner 재검증, expected turn 검증, optimistic locking, turn unique constraint를 함께 사용하므로 이중 commit을 허용하지 않는다.
