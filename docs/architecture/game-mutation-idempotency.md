# Game mutation idempotency

`POST /api/game/init`과 `POST /api/game/progress`는 비용이 발생하고 canonical game state를 변경하므로 명시적인 idempotency 계약을 사용합니다.

## HTTP contract

- 클라이언트는 `Idempotency-Key` header를 보냅니다.
- 허용 형식: `[A-Za-z0-9._:-]{8,128}`.
- web client는 `crypto.randomUUID()`로 사용자 mutation 하나당 key 하나를 만듭니다.
- 같은 사용자 mutation의 network/provider/persistence retry는 같은 key를 재사용합니다.
- 새 게임 시작이나 새 action은 새 key를 사용합니다.
- 한 owner 안에서는 `/init`과 `/progress`를 포함한 모든 game mutation에서 key를 재사용하지 않습니다.
- GET 및 image asset fetch에는 이 header를 요구하지 않습니다.

retry identity는 world/character/action payload 자체가 아니라 HTTP mutation delivery 계약이므로 transport metadata를 DTO에 섞지 않고 header로 관리합니다.

## Persistence contract

`game_mutation_request`는 append-only canonical `GameLog`와 별개의 request-processing record입니다.

저장 항목:

- owner key
- operation (`INIT`, `PROGRESS`)
- idempotency key
- session / expected turn
- SHA-256 request fingerprint
- processing status (`PROCESSING`, `COMPLETED`, `FAILED`)
- 완료 결과를 찾기 위한 session / turn / title

`(owner_key, idempotency_key)`는 PostgreSQL unique constraint로 보호합니다. operation도 fingerprint와 함께 검증하므로 같은 owner가 동일 key를 다른 endpoint에 재사용해도 conflict입니다.

완료된 같은 key + 같은 operation/fingerprint는 provider를 다시 호출하지 않고 result session/turn의 canonical `GameLog`를 읽어 `GameResponse`를 재구성합니다. 다른 operation 또는 다른 fingerprint에 사용하면 provider 호출 전에 `409 IDEMPOTENCY_CONFLICT`로 거절합니다. 실패한 동일 payload request는 같은 key로 재시도할 수 있습니다.

### Completed replay와 최신 세션 동기화

- 성공적인 **신규** init/progress 응답은 `replayed: false`; 완료된 key의 **재전송** 응답은 `replayed: true`입니다. `GET /api/game/sessions/{sessionId}` 안의 `game.replayed`는 항상 `false`입니다.
- 완료된 replay의 `sessionId`, `turnNumber`, `title`, `storyText`, `choices`, `mainImageUrl`, `latestSkillCheck`는 **원래 저장된 턴**에 속합니다. 최신 턴으로 바꿔서 돌려주지 않습니다.
- replay 시 현재 snapshot의 턴이 replay 턴과 일치할 때만 stats/gameplay를 투영합니다. 더 최신이면 `characterStats: []`, `gameplay: null`이며, 최신 데이터를 과거 데이터로 혼합하지 않습니다. `replayed`는 턴이 같은 경우에도 `true`입니다.
- 프론트엔드는 `replayed: true`를 받으면 **항상** 동일 owner의 읽기 전용 `GET /api/game/sessions/{sessionId}`를 요청하여 현재 완료 턴과 `canProgress` 상태를 반영합니다. 원본 replay 응답만으로 다음 `expectedTurn`을 만들지 않습니다. `turnNumber = canonicalStateTurn = game.turnNumber` 및 세션 ID 일치를 확인하고, 불일치·조회 실패 시 이전 replay를 현재 턴으로 표시하지 않습니다.
- init 및 progress 모두 이 계약을 사용합니다. GET은 provider 호출도 canonical commit도 하지 않습니다. 새 API field는 additive이며 backend를 먼저 배포하고 frontend를 이어서 배포합니다.

과거 응답 예시: `{ "sessionId": 42, "turnNumber": 2, "storyText": "과거 장면", "replayed": true, "characterStats": [], "gameplay": null }`. GET resume가 현재 4턴을 반환하면 UI의 다음 `expectedTurn`은 4이며, 2턴의 선택지는 재사용하지 않습니다.

`GameLog`에는 request processing state를 저장하지 않습니다.

## `/progress`와 turn reservation

현재 `/progress`는 idempotency와 별도로 `(session_id, expected_turn)` turn reservation을 사용합니다.

1. mutation request를 식별하고 fingerprint를 검증합니다.
2. 같은 canonical turn의 reservation lease를 획득합니다.
3. session/turn과 서버 발급 action을 검증합니다.
4. rate limit과 provider budget guard를 통과합니다.
5. 실제 Narrative provider 실행 직전에 reservation owner를 다시 확인하며 `provider_attempt_count`를 증가시킵니다.
6. provider 응답 이후 canonical commit 시 같은 reservation owner인지 다시 검증합니다.

active lease가 존재하면 다른 요청은 provider에 진입할 수 없습니다. lease expiry 후 takeover는 가능하지만 stale owner는 canonical commit을 수행할 수 없습니다.

reservation 획득 횟수와 provider 실행 횟수는 분리합니다. validation, stale action, rate limit, budget guard 같은 pre-provider 실패는 provider quota를 소비하지 않으며 실제 provider 시작은 같은 turn에서 최대 3회입니다.

세부 계약은 [game-turn-reservation.md](./game-turn-reservation.md)를 기준으로 합니다.

## Transaction boundary

provider network call은 DB transaction 밖에서 수행합니다. canonical session/snapshot/GameLog 저장과 request `COMPLETED` 전환은 같은 persistence transaction에서 수행합니다. 따라서 응답 전송만 유실된 retry는 완료 request를 찾아 기존 결과를 replay할 수 있습니다.

외부 provider 호출과 DB transaction을 하나의 원자적 transaction으로 묶지는 않습니다. provider 성공 후 process crash가 발생하면 lease expiry 후 provider 재호출 가능성이 있으므로 외부 호출 strict exactly-once가 아니라 bounded at-least-once입니다. canonical DB 결과는 reservation owner, expected turn, optimistic locking과 unique constraint를 통해 하나로 수렴합니다.

## Retention

request record의 운영 보존 기간은 **24시간 이상**으로 정의합니다. 현재 자동 cleanup job은 없으며, 향후 cleanup을 추가하더라도 24시간보다 이른 삭제는 허용하지 않습니다.

## Deployment compatibility

`Idempotency-Key`가 필수가 된 초기 전환에서는 frontend-first 배포가 필요했습니다. 현재 production frontend와 backend는 모두 해당 계약을 사용합니다.

CORS allowlist에는 `Idempotency-Key`가 포함되어야 하며, 향후 mutation transport 계약을 변경할 때도 frontend/backend의 배포 순서와 rollback 호환성을 함께 검토합니다.
