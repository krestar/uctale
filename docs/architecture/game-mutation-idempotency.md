# Game mutation idempotency

`POST /api/game/init`과 `POST /api/game/progress`는 비용이 발생하고 canonical game state를 변경하므로 명시적인 idempotency 계약을 사용한다.

## HTTP contract

- 클라이언트는 `Idempotency-Key` header를 보낸다.
- 허용 형식: `[A-Za-z0-9._:-]{8,128}`.
- web client는 `crypto.randomUUID()`로 사용자 mutation 하나당 key 하나를 만든다.
- 같은 사용자 mutation의 network/provider/persistence retry는 같은 key를 재사용한다.
- 새 게임 시작이나 새 choice는 새 key를 사용한다.
- 한 owner 안에서는 `/init`과 `/progress`를 포함한 모든 game mutation에서 key를 재사용하지 않는다.
- GET 및 image asset fetch에는 이 header를 요구하지 않는다.

Header를 선택한 이유는 retry identity가 world/character/choice라는 게임 payload 자체가 아니라 HTTP mutation delivery 계약이기 때문이다. DTO에 transport-level retry metadata를 섞지 않고 `/init`과 `/progress`에 같은 규칙을 적용할 수 있다.

## Persistence contract

`game_mutation_request`는 append-only canonical `GameLog`와 별개의 request-processing record다.

저장 항목:

- owner key
- operation (`INIT`, `PROGRESS`)
- idempotency key
- session / expected turn (해당 시)
- SHA-256 request fingerprint
- processing status (`PROCESSING`, `COMPLETED`, `FAILED`)
- 완료 결과를 찾기 위한 session / turn / title

`(owner_key, idempotency_key)`는 PostgreSQL unique constraint로 보호한다. operation도 fingerprint 검증과 함께 비교하므로 같은 owner가 동일 key를 다른 endpoint에 재사용해도 conflict다. request fingerprint에는 raw world/character/story 전문을 저장하지 않는다. init fingerprint는 operation과 world/character의 line-ending-normalized 값을 length-prefix 형식으로 결합한 뒤 SHA-256으로 해시한다. progress fingerprint는 operation/session/choice/expectedTurn을 같은 방식으로 해시한다.

완료된 같은 key + 같은 operation/fingerprint는 provider를 다시 호출하지 않고 result session/turn의 canonical `GameLog`를 읽어 `GameResponse`를 재구성한다. 같은 key가 다른 operation 또는 다른 fingerprint에 사용되면 provider 호출 전에 `409 IDEMPOTENCY_CONFLICT`로 거절한다. 실패한 동일 payload request는 같은 key로 다시 시도할 수 있다.

`GameLog`에는 idempotency processing state를 저장하지 않는다.

## Transaction boundary

provider network call은 DB transaction 밖에서 수행한다. canonical session/snapshot/GameLog 저장과 request `COMPLETED` 전환은 같은 persistence transaction에서 수행한다. 따라서 응답 전송만 유실된 retry는 완료 request를 찾아 기존 결과를 재생할 수 있다.

동시에 들어온 최초 요청을 reservation/lease로 정확히 하나의 provider 실행에 제한하거나 `PROCESSING` 상태의 대기/재시도 정책을 제공하는 것은 #30 범위다. #29는 완료된 retry reuse, payload/operation conflict, DB uniqueness 계약을 고정한다. 따라서 provider 완료 전에 프로세스가 중단된 `PROCESSING` 요청은 이후 retry에서 provider를 다시 호출할 수 있다.

## Retention

request record의 운영 보존 기간은 **24시간 이상**으로 정의한다. 현재 M2에서는 자동 삭제 job을 추가하지 않고 보존한다. 향후 cleanup을 추가할 때도 24시간보다 이른 삭제는 허용하지 않는다.

## Deployment order

기존 production frontend는 `Idempotency-Key`를 보내지 않으므로 backend를 먼저 배포하면 `/init`과 `/progress`가 400이 될 수 있다. 한 번의 전환에서는 다음 순서를 사용한다.

1. **frontend first**: 새 frontend를 배포한다. 기존 backend는 추가 header를 무시하므로 호환된다.
2. **backend second**: V7 migration과 필수 `Idempotency-Key` 계약을 포함한 backend를 배포한다.

CORS allowlist에도 `Idempotency-Key`가 포함되어야 한다.
