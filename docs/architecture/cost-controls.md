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

### Rate limit 운영 한계

이 limiter들은 Render 인스턴스 하나의 메모리에만 존재한다. 인스턴스가 여러 개가 되면 각 인스턴스가 독립 quota/counter를 가지므로 전역 한도를 보장하지 않는다. 현재 단일 인스턴스 공유 베타에 맞춘 burst 안전장치이며, multi-instance 전환 시 Redis 등 외부 shared store 기반 limiter로 교체한다.

## 전역 AI budget guard

#71부터 per-owner/IP/session rate limit과 별도로 PostgreSQL 기반 전역 usage ledger를 유지한다. 이 경계의 목적은 순간 burst 제한이 아니라 일/월 단위 총 provider 사용량을 관측하고 운영 예산 임계치를 적용하는 것이다.

### Budget metric

초기 공유 베타에서는 provider 청구 금액을 직접 계산하지 않고 `budget unit`을 사용한다.

- 실제 provider attempt 1회에 operation별 설정 unit을 곱한다.
- `attemptCount = retryCount + 1`이므로 최초 호출과 내부 retry가 모두 집계된다.
- 성공/실패 모두 provider가 실제 호출되었다면 usage ledger에 기록한다.
- ledger에는 `provider`, `model`, `operation`, `outcome`, `attempt_count`, `budget_units`, 발생 시각만 저장한다.
- prompt/response 전문, API key, 비밀번호, access/owner token은 저장하지 않는다.

기본 단위는 Narrative/Image 모두 attempt당 1 unit이다. provider 단가 차이를 반영해야 할 때 배포 설정으로 가중치를 조정한다.

환경변수:

- `GAME_COST_BUDGET_DAILY_WARNING_UNITS`
- `GAME_COST_BUDGET_DAILY_CRITICAL_UNITS`
- `GAME_COST_BUDGET_MONTHLY_WARNING_UNITS`
- `GAME_COST_BUDGET_MONTHLY_CRITICAL_UNITS`
- `GAME_COST_BUDGET_NARRATIVE_ATTEMPT_UNITS`
- `GAME_COST_BUDGET_IMAGE_ATTEMPT_UNITS`
- `GAME_COST_BUDGET_CRITICAL_MODE` (`ALERT_ONLY` 또는 `FAIL_CLOSED`)

일/월 경계는 UTC 기준이다.

### Alert와 차단 정책

usage 기록 후 warning 또는 critical threshold를 처음 넘어가는 호출에서 구조화 로그 `ai_budget_alert`를 생성한다.

- warning: `level=WARNING`
- critical: `level=CRITICAL`
- ledger 저장 실패: `level=ACCOUNTING_FAILURE`

기본 `critical-mode`는 `ALERT_ONLY`다. 공유 베타에서 임계치 설정 오류나 일시적 사용량 증가만으로 게임을 갑자기 중단하지 않기 위한 선택이다.

`FAIL_CLOSED`로 설정하면 신규 provider 호출 직전에 현재 UTC 일/월 usage와 다음 1회 attempt unit을 조회하고 critical을 넘는 호출을 `503 AI_BUDGET_EXCEEDED`로 차단한다. provider 내부 retry는 한 번의 adapter 호출 안에서 발생하므로 retry가 임계치를 넘긴 경우 해당 호출 자체를 중간 취소하지 않고, 실제 retry 수를 사후 ledger에 반영한 뒤 다음 신규 호출부터 차단한다.

### 정확도와 multi-instance 경계

usage ledger와 일/월 합계는 PostgreSQL을 사용하므로 application 재시작으로 초기화되지 않으며 여러 application instance가 같은 DB를 사용할 때도 완료된 provider attempt 집계는 공유된다.

다만 `FAIL_CLOSED`의 pre-check와 실제 provider 호출 사이에는 DB lock을 장시간 유지하지 않는다. 따라서 여러 instance가 critical 직전에서 동시에 호출하면 소수의 동시 요청이 임계치를 초과할 수 있다. 외부 provider 호출 동안 DB transaction/advisory lock을 유지하는 것은 connection pool과 장애 격리 비용이 더 크므로 M2 범위에서 제외한다. 엄격한 hard cap이 필요해지면 provider 호출 reservation 또는 distributed budget token store를 별도 도입한다.

## Provider 관측성

provider 호출마다 다음 항목을 구조화된 key/value 로그로 기록한다.

- provider
- model
- operation
- sessionId
- turn
- requestId
- idempotencyKey
- latencyMs
- outcome (`SUCCESS` / `FAILURE`)
- retryCount
- attemptCount

사용자 world/character/action, provider prompt/응답 전문, access/owner token, API key는 로그에 기록하지 않는다.

Narrative는 현재 provider 내부 retry가 없어 `retryCount=0`이다. Image는 #50부터 Pollinations bounded retry의 실제 횟수를 성공 결과 또는 최종 실패에서 추출해 같은 `provider_call` event에 기록한다. 이미지별 model/size/seed/status 등 상세 진단은 `image_provider_result` event를 함께 사용한다.

## 책임 경계

- Access authentication: `AccessController`가 `ClientIpResolver`로 IP를 식별하고 `AccessAuthenticationRateLimiter` 안에서 비밀번호 판정과 실패 counter를 원자적으로 처리한다.
- Per-request cost rate limit (#27): owner/IP/session별 짧은 fixed window에서 burst와 반복 요청을 제한한다. 단일 instance 메모리 경계다.
- Global budget guard (#71): PostgreSQL usage ledger를 기준으로 UTC 일/월 전체 provider 사용량을 집계하고 warning/critical 운영 신호와 선택적 fail-closed를 제공한다.
- Narrative: `GameService`가 소유권/turn 조회 후 rate limit을 확인하고 Gemini 호출만 telemetry로 감싼다.
- Image: `ImageAssetService`가 asset 소유권과 기존 생성 결과를 먼저 확인한다. 미생성 asset에 대해서만 lock 내부에서 rate limit을 확인하고 Pollinations 호출을 telemetry로 감싼다.
- DB에 저장된 이미지 재조회는 provider를 호출하지 않으므로 Image quota와 global budget unit을 소비하지 않는다.
