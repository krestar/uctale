# 비용 API rate limit과 provider 관측성

## 목적

공유 베타에서 인증된 사용자의 반복 클릭·자동화 요청·재시도로 Narrative/Image provider 비용이 급증하는 상황을 줄이고, 실제 provider 호출을 turn 단위로 추적합니다.

## Per-request rate limit

현재 구현은 단일 application instance 메모리 안에서 동작하는 fixed-window limiter입니다.

- Narrative와 Image quota를 분리합니다.
- owner principal, client IP, session(존재할 때) 각각에 operation quota를 적용합니다.
- 어느 bucket 하나라도 한도를 넘으면 provider 호출 전에 `429 RATE_LIMIT_EXCEEDED`를 반환합니다.
- 다음 window까지 남은 초를 `Retry-After` header로 제공합니다.
- 이미 DB에 저장된 image asset 조회는 provider 비용이 없으므로 quota를 소비하지 않습니다.

기본값:

- window: 60초
- Narrative: 12회/window
- Image: 8회/window

환경변수:

- `GAME_COST_RATE_LIMIT_WINDOW_SECONDS`
- `GAME_COST_NARRATIVE_LIMIT`
- `GAME_COST_IMAGE_LIMIT`

### 접근 비밀번호 인증 rate limit

`POST /api/game/verify-password`는 비용 API quota와 별도의 IP 기반 실패 limiter를 사용합니다.

- 기본값은 300초 window에서 연속 실패 5회입니다.
- 한도를 넘으면 `429 ACCESS_RATE_LIMIT_EXCEEDED`와 `Retry-After`를 반환합니다.
- 정상 인증 성공 시 해당 IP의 실패 counter를 초기화합니다.
- 비밀번호 판정과 실패 counter 갱신은 하나의 limiter 경계에서 처리합니다.
- 비밀번호, request body, access/owner token은 limiter 로그에 남기지 않습니다.

환경변수:

- `GAME_ACCESS_RATE_LIMIT_FAILURE_LIMIT`
- `GAME_ACCESS_RATE_LIMIT_WINDOW_SECONDS`

### Rate limit 운영 한계

이 limiter들은 Render application instance 하나의 메모리에 존재합니다. 여러 instance가 되면 각 instance가 독립 quota/counter를 가지므로 전역 한도를 보장하지 않습니다. 현재 단일 instance 공유 베타의 burst 안전장치이며 multi-instance 전환 시 external shared store 기반 limiter를 검토합니다.

## 전역 AI budget guard

Per-owner/IP/session rate limit과 별도로 PostgreSQL `provider_usage_event` ledger를 유지합니다. 이 경계의 목적은 순간 burst 제한이 아니라 일/월 단위 전체 provider 사용량을 누적하고 운영 예산 임계치를 적용하는 것입니다.

### Budget metric

현재는 provider 청구 금액을 직접 계산하지 않고 `budget unit`을 사용합니다.

- 실제 provider attempt 1회에 operation별 설정 unit을 곱합니다.
- adapter 내부 retry도 실제 시도 수에 포함합니다.
- 성공/실패 모두 provider가 실제 호출되었다면 ledger에 기록합니다.
- ledger에는 provider/model/operation/outcome/attempt count/budget units/발생 시각만 저장합니다.
- prompt/response 전문, API key, 비밀번호, access/owner token은 저장하지 않습니다.

환경변수:

- `GAME_COST_BUDGET_DAILY_WARNING_UNITS`
- `GAME_COST_BUDGET_DAILY_CRITICAL_UNITS`
- `GAME_COST_BUDGET_MONTHLY_WARNING_UNITS`
- `GAME_COST_BUDGET_MONTHLY_CRITICAL_UNITS`
- `GAME_COST_BUDGET_NARRATIVE_ATTEMPT_UNITS`
- `GAME_COST_BUDGET_IMAGE_ATTEMPT_UNITS`
- `GAME_COST_BUDGET_CRITICAL_MODE` (`ALERT_ONLY` 또는 `FAIL_CLOSED`)

일/월 경계는 UTC입니다.

### Alert와 차단 정책

usage 기록 후 warning 또는 critical threshold를 처음 넘어가는 호출에서 구조화 로그 `ai_budget_alert`를 생성합니다.

- warning: `level=WARNING`
- critical: `level=CRITICAL`
- ledger 저장 실패: `level=ACCOUNTING_FAILURE`

기본 `critical-mode`는 `ALERT_ONLY`입니다. `FAIL_CLOSED`에서는 신규 provider 호출 직전에 현재 usage와 다음 1회 unit을 조회하고 critical을 넘는 신규 호출을 `503 AI_BUDGET_EXCEEDED`로 차단합니다.

Narrative `/progress`에서는 budget guard가 provider attempt accounting보다 먼저 실행됩니다. 따라서 budget pre-call rejection은 turn reservation의 `provider_attempt_count`를 소비하지 않습니다.

provider 내부 retry는 하나의 adapter 호출 안에서 발생하므로 retry 도중 threshold를 넘더라도 해당 호출을 중간 취소하지 않고 실제 retry 수를 사후 ledger에 반영한 뒤 다음 신규 호출부터 차단합니다.

### 정확도와 multi-instance 경계

usage ledger와 일/월 합계는 PostgreSQL을 사용하므로 application 재시작으로 초기화되지 않고 여러 application instance가 같은 DB를 사용할 때 완료된 usage 집계는 공유됩니다.

다만 `FAIL_CLOSED` pre-check와 실제 provider 호출 사이에는 장기 DB lock을 유지하지 않습니다. 여러 instance가 critical 직전에서 동시에 호출하면 소수의 동시 요청이 threshold를 초과할 수 있습니다. 외부 provider 호출 동안 DB transaction/advisory lock을 유지하는 대신 현재는 availability와 DB connection 비용을 우선합니다. 엄격한 hard cap이 필요해지면 distributed budget token/reservation을 별도 설계합니다.

## Provider 관측성

provider 호출마다 다음 항목을 구조화 로그로 기록합니다.

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

사용자 world/character/action, provider prompt/응답 전문, access/owner token, API key는 로그에 기록하지 않습니다.

Narrative는 현재 provider 내부 retry가 없어 `retryCount=0`입니다. Image는 Pollinations bounded retry의 실제 횟수를 성공 결과 또는 최종 실패에서 추출해 같은 `provider_call` event에 기록합니다. 이미지별 model/size/seed/status 등 상세 진단은 `image_provider_result` event를 사용합니다.

## 책임 경계

- Access authentication: `AccessAuthenticationRateLimiter`가 비밀번호 실패 burst를 제한합니다.
- Per-request cost rate limit: owner/IP/session별 짧은 fixed window에서 반복 요청을 제한합니다.
- Global budget guard: PostgreSQL usage ledger를 기준으로 UTC 일/월 provider 사용량과 warning/critical 정책을 관리합니다.
- Turn provider attempt: `game_turn_reservation.provider_attempt_count`가 같은 canonical turn의 실제 Narrative provider 시작을 최대 3회로 제한합니다. 이는 전역 budget ledger와 별개의 무결성 경계입니다.
- Narrative: `GameService`가 소유권/turn/action/rate limit을 확인하고 budget guard와 provider attempt 시작 경계를 통과한 호출만 Gemini telemetry로 감쌉니다.
- Image: `ImageAssetService`가 asset 소유권과 기존 생성 결과를 먼저 확인하고 미생성 asset에 대해서만 rate limit과 provider 호출을 수행합니다.
- DB에 저장된 이미지 재조회는 provider를 호출하지 않으므로 Image quota와 global budget unit을 소비하지 않습니다.
