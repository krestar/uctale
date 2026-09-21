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
- 기본 lease: 180초 (`app.game.turn-reservation.lease-seconds`)
- 한 recovery window의 최대 provider 시도: 3회
- 기본 recovery cooldown: 30초 (`app.game.turn-reservation.recovery-cooldown-seconds`)
- `provider_attempt_count`: 현재 recovery window에서 실제 Narrative provider 실행 경계에 진입한 횟수
- `recovery_available_at`: provider 3회 소진 또는 명시적 cooldown 뒤 다음 획득이 허용되는 시각
- `attempt_count`: V8에서 생성된 legacy lease-attempt 필드로, 신규 provider 상한 판정에는 사용하지 않는다.

서로 다른 idempotency key가 같은 turn을 요청해도 lease가 유효하면 후발 요청은 provider를 호출하지 않고 `409 MUTATION_IN_PROGRESS`를 받는다.

Reservation 획득 자체는 provider attempt를 소비하지 않는다. session/turn 검증, choice/action 검증, rate limit, 전역 provider budget guard처럼 provider 호출 전에 끝나는 실패는 lease만 실패/만료 처리하며 `provider_attempt_count`를 증가시키지 않는다.

모든 pre-provider 검증과 budget guard를 통과한 뒤 Narrative provider를 실제 호출하기 직전에 현재 `request_id`/`lease_owner`가 여전히 유효한지 확인하면서 `provider_attempt_count`를 원자적으로 증가시킨다. 이미 3회에 도달했거나 lease가 만료/회수된 owner는 현재 window의 provider 호출 구간에 진입할 수 없다.

3회를 소진하면 reservation을 영구 차단하지 않는다. `recovery_available_at`까지 새 획득을 막고, 그 시각 이후 lease takeover가 성공할 때만 `provider_attempt_count`를 0으로 초기화해 새 recovery window를 시작한다. cooldown 자체가 호출 수를 지우지 않으며 stale owner는 새 owner가 발급되는 순간 계속 fence된다.

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

Provider 실행 경계에 진입한 뒤 provider 또는 commit 전 단계에서 예외가 발생하면 해당 시도는 `provider_attempt_count`에 남고 mutation request를 `FAILED`로 바꾸며 lease를 즉시 만료시켜 남은 횟수 범위에서 재시도를 허용한다.

프로세스가 provider 시작 표시 직후 또는 provider 성공 직후 DB commit 전에 죽으면 실패 처리를 실행할 수 없다. 이 경우 lease가 만료된 뒤 다른 요청이 reservation을 회수할 수 있고 provider가 다시 호출될 수 있다. 따라서 외부 provider 호출은 strict exactly-once가 아니라 **bounded at-least-once**이며, 한 recovery window에서 최대 3번으로 제한한다. 소진 뒤에는 cooldown 이후 새 window에서 다시 진행할 수 있다.

provider 시작 표시와 실제 네트워크 I/O 사이의 프로세스 crash는 보수적으로 provider attempt를 소비한 것으로 본다. 이 경계를 DB transaction과 외부 provider 호출 사이에서 완전히 원자화할 수 없기 때문에, 중복 호출 상한을 지키는 방향으로 fail-safe 한다.

V10 이전에 쌓인 `attempt_count`는 lease 획득 횟수였기 때문에 provider 호출 여부를 정확히 복원할 수 없다. V10은 이를 재해석하거나 초기화하지 않고 `provider_attempt_count`를 별도 추가해 배포 시 기존 데이터가 신규 상한을 잘못 소모하지 않도록 한다.

반면 canonical state와 GameLog는 reservation owner 재검증, expected turn 검증, optimistic locking, turn unique constraint를 함께 사용하므로 이중 commit을 허용하지 않는다.


## Timeout / lease 정합성

Narrative 실행 정책은 startup에서 Gemini connect/read timeout과 최대 3회 시도, 50ms/150ms inline backoff의 최악 처리 시간을 계산한다. 기본값은 connect 10초 + read 40초, 3회와 backoff를 합쳐 150.2초이며 reservation lease 기본값 180초가 이보다 길어야 한다. lease가 최악 처리 시간 이하이면 애플리케이션은 시작 단계에서 설정 오류로 실패한다.

HTTP 429는 50ms/150ms inline backoff를 적용하지 않는다. 명시적 recovery cooldown으로 전환하고 API는 `503 NARRATIVE_PROVIDER_RECOVERY_WAIT`와 `Retry-After`를 반환한다. 408/5xx/network transient failure는 현재 window 안에서 bounded retry를 수행하고 3회가 소진되면 같은 cooldown 경계로 전환한다.
