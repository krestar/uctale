# 비용 API rate limit과 provider 관측성

## 목적

공유 베타에서 인증된 사용자의 반복 클릭·자동화 요청·재시도로 Narrative/Image provider 비용이 급증하는 상황을 줄이고, 실제 provider 호출을 turn 단위로 추적할 수 있게 한다.

## Rate limit

현재 구현은 단일 application instance 메모리 안에서 동작하는 fixed-window limiter다.

- Narrative와 Image quota를 서로 분리한다.
- owner principal, client IP, session(존재할 때) 각각에 같은 operation quota를 적용한다.
- 어느 bucket 하나라도 한도를 넘으면 provider 호출 전에 `429 RATE_LIMIT_EXCEEDED`를 반환한다.
- 응답에는 다음 window까지 남은 초를 `Retry-After` 헤더로 제공한다.
- 이미 생성되어 DB에 저장된 image asset 조회는 provider 비용이 들지 않으므로 quota를 소비하지 않는다.

기본값:

- window: 60초
- Narrative: 12회/window
- Image: 8회/window

환경변수:

- `GAME_COST_RATE_LIMIT_WINDOW_SECONDS`
- `GAME_COST_NARRATIVE_LIMIT`
- `GAME_COST_IMAGE_LIMIT`

### 접근 비밀번호 인증 rate limit

`POST /api/game/verify-password`는 비용 API quota와 별도의 IP 기반 실패 limiter를 사용한다.

- 기본값은 300초 window에서 연속 실패 5회까지 허용하고, 다음 인증 시도부터 `429 ACCESS_RATE_LIMIT_EXCEEDED`를 반환한다.
- 응답에는 현재 fixed window가 끝날 때까지의 초를 `Retry-After`로 제공한다.
- 정상 인증이 성공하면 해당 IP의 실패 counter를 초기화한다.
- 비밀번호 검증과 실패 counter 갱신은 하나의 원자적 limiter 경계에서 처리해 동시 요청 burst가 실패 한도를 우회하지 못하게 한다.
- client IP는 비용 limiter와 동일한 `ClientIpResolver` 경계를 재사용한다.
- 인증 실패는 Narrative/Image quota를 소비하지 않는다.
- 비밀번호, request body, access/owner token은 limiter에서 로그로 남기지 않는다.

환경변수:

- `GAME_ACCESS_RATE_LIMIT_FAILURE_LIMIT`
- `GAME_ACCESS_RATE_LIMIT_WINDOW_SECONDS`

### 운영 한계

이 limiter들은 Render 인스턴스 하나의 메모리에만 존재한다. 인스턴스가 여러 개가 되면 각 인스턴스가 독립 quota/counter를 가지므로 전역 한도를 보장하지 않는다. 현재 단일 인스턴스 공유 베타에 맞춘 최소 안전장치이며, multi-instance 전환 시 Redis 등 외부 shared store 기반 limiter로 교체한다.

## Provider 관측성

provider 호출마다 다음 항목을 구조화된 key/value 로그로 기록한다.

- provider
- operation
- sessionId
- turn
- requestId
- idempotencyKey(현재는 미도입이므로 `-`)
- latencyMs
- outcome (`SUCCESS` / `FAILURE`)
- retryCount

사용자 world/character/action, provider prompt/응답 전문, access/owner token, API key는 로그에 기록하지 않는다.

Narrative는 현재 provider 내부 retry가 없어 `retryCount=0`이다. Image는 #50부터 Pollinations bounded retry의 실제 횟수를 성공 결과 또는 최종 실패에서 추출해 같은 `provider_call` event에 기록한다. 이미지별 model/size/seed/status 등 상세 진단은 `image_provider_result` event를 함께 사용한다.

## 책임 경계

- Access authentication: `AccessController`가 `ClientIpResolver`로 IP를 식별하고 `AccessAuthenticationRateLimiter` 안에서 비밀번호 판정과 실패 counter를 원자적으로 처리한다.
- Narrative: `GameService`가 소유권/turn 조회 후 rate limit을 확인하고 Gemini 호출만 telemetry로 감싼다.
- Image: `ImageAssetService`가 asset 소유권과 기존 생성 결과를 먼저 확인한다. 미생성 asset에 대해서만 lock 내부에서 rate limit을 확인하고 Pollinations 호출을 telemetry로 감싼다.
- DB에 저장된 이미지 재조회는 provider를 호출하지 않으므로 Image quota를 소비하지 않는다.
