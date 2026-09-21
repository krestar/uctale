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

### 접근 비밀번호 인증과 owner identity 발급 rate limit

`POST /api/game/verify-password`에는 서로 다른 두 책임의 limiter가 있습니다.

- `AccessAuthenticationRateLimiter`: 잘못된 비밀번호 반복 시도를 IP 기준으로 제한합니다. 기본값은 300초 window에서 연속 실패 5회이며, 한도를 넘으면 `429 ACCESS_RATE_LIMIT_EXCEEDED`와 `Retry-After`를 반환합니다.
- `OwnerIdentityIssuanceRateLimiter`: 비밀번호가 맞더라도 유효한 owner token 없이 새 owner identity를 발급하는 횟수를 client IP 기준으로 제한합니다. 기본값은 3600초 window에서 5회이며, 한도를 넘으면 `429 OWNER_ISSUANCE_RATE_LIMIT_EXCEEDED`와 `Retry-After`를 반환합니다.
- 유효한 owner token을 가진 재인증은 기존 owner key를 재사용하므로 owner identity 발급 quota를 소비하지 않습니다.
- 비밀번호 성공은 authentication failure counter를 초기화합니다. 이후 새 owner 발급 여부는 별도 issuance limiter가 결정하므로 두 책임과 counter는 섞이지 않습니다.
- 비밀번호, request body, access/owner token은 limiter 로그에 남기지 않습니다.

환경변수:

- `GAME_ACCESS_RATE_LIMIT_FAILURE_LIMIT`
- `GAME_ACCESS_RATE_LIMIT_WINDOW_SECONDS`
- `GAME_OWNER_ISSUANCE_RATE_LIMIT`
- `GAME_OWNER_ISSUANCE_RATE_LIMIT_WINDOW_SECONDS`

### Client IP 신뢰 경계

비용/인증 bucket의 client IP는 caller가 임의로 공급할 수 있는 forwarded chain을 직접 신뢰하지 않습니다.

- Render public web service에서는 Render가 자동으로 제공하는 `RENDER=true`를 기본 신호로 사용해 `CF-Connecting-IP`를 신뢰합니다. Render의 public ingress는 Cloudflare를 통과하며 이 header는 edge에서 caller 값보다 우선해 설정되는 계약을 사용합니다.
- Render가 아닌 환경에서는 기본적으로 socket `remoteAddr`를 사용합니다.
- `X-Forwarded-For`는 bucket identity 입력으로 사용하지 않습니다. caller가 보낸 chain 값으로 left-most identity를 회전시키는 우회를 허용하지 않기 위함입니다.
- `GAME_CLIENT_IP_TRUST_RENDER_PROXY_HEADER`로 자동 동작을 명시적으로 override할 수 있습니다. production topology가 Render public web service가 아닌 형태로 바뀌면 proxy contract를 다시 검토해야 합니다.

### Request resource limit

provider 호출 이전에 비정상적으로 큰 JSON 요청을 제한합니다.

- `/api/**`의 `POST/PUT/PATCH` body는 caller가 지정하는 `Content-Type`과 무관하게 기본 16 KiB 상한을 갖습니다. JSON API의 media type을 바꾸는 방식으로 이 경계를 우회할 수 없습니다. 초과 시 MVC controller 진입 전에 `413 REQUEST_BODY_TOO_LARGE`로 거부합니다.
- `GameProgressRequest.arguments`는 최대 8개 entry, key 최대 64자, value 최대 256자로 제한합니다.
- arguments validation 실패는 `400 VALIDATION_ERROR`이며 `GameService`, rate limit, budget guard, provider attempt 경계에 도달하지 않습니다.

환경변수:

- `GAME_REQUEST_MAX_JSON_BODY_BYTES`

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

기본 `critical-mode`는 `ALERT_ONLY`입니다. repository 기본 threshold는 daily warning/critical 500/750 units, monthly warning/critical 10000/15000 units입니다. `FAIL_CLOSED`에서는 신규 provider 호출 직전에 현재 usage와 다음 1회 unit을 조회하고 critical을 넘는 신규 호출을 `503 AI_BUDGET_EXCEEDED`로 차단합니다.

### Production budget 운영 상태

2026-09-22 기준 코드와 GitHub 저장소에서 확인할 수 있는 값은 위 repository default까지입니다. Render Dashboard의 production environment 변수 값은 저장소에서 읽을 수 없으므로 실제 배포의 `GAME_COST_BUDGET_*` override 유무를 repository default와 동일하다고 추측하지 않습니다. 배포 설정 확인 전에는 production mode/threshold 검증 완료로 취급하지 않습니다.

또한 #126의 Story Memory summary retry provider usage 회계가 실제 physical invocation 수와 1:1로 정합화되기 전에는 `FAIL_CLOSED` 전환을 하지 않습니다. #126 완료 후 실제 production ledger와 threshold를 다시 확인하고 `FAIL_CLOSED` 활성화 여부를 결정합니다.

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

Narrative bounded recovery는 같은 logical call 안의 실제 Gemini provider attempt 수를 `retryCount`/`attemptCount`에 반영합니다. `provider_call.model`은 `GOOGLE_AI_MODEL` 설정과 동일한 model ID를 기록합니다. Gemini adapter는 각 provider attempt의 별도 `gemini_provider_result` 로그에 model, thinking level, latency, outcome과 provider가 반환한 prompt/candidate/thought/total token usage를 기록합니다. 토큰 메타데이터가 없거나 provider 호출 자체가 실패한 경우 해당 token 값은 비어 있을 수 있습니다. prompt/story 전문은 기록하지 않습니다.

Image는 Pollinations bounded retry의 실제 횟수를 성공 결과 또는 최종 실패에서 추출해 같은 `provider_call` event에 기록합니다. 이미지별 model/size/seed/status 등 상세 진단은 `image_provider_result` event를 사용합니다.

## 책임 경계

- Access authentication: `AccessAuthenticationRateLimiter`가 비밀번호 실패 burst를 제한합니다.
- Owner identity issuance: `OwnerIdentityIssuanceRateLimiter`가 올바른 공유 비밀번호를 반복 사용해 새 owner identity를 회전시키는 빈도를 제한합니다.
- Client IP: Render public ingress에서는 `CF-Connecting-IP`, 그 외 기본 환경에서는 `remoteAddr`를 사용하며 `X-Forwarded-For`를 cost bucket identity로 사용하지 않습니다.
- Request resource boundary: `JsonRequestBodySizeFilter`와 DTO validation이 큰 JSON body/arguments를 service/provider 이전에 차단합니다.
- Per-request cost rate limit: owner/IP/session별 짧은 fixed window에서 반복 요청을 제한합니다.
- Global budget guard: PostgreSQL usage ledger를 기준으로 UTC 일/월 provider 사용량과 warning/critical 정책을 관리합니다.
- Turn provider attempt: `game_turn_reservation.provider_attempt_count`가 같은 canonical turn의 실제 Narrative provider 시작을 최대 3회로 제한합니다. 이는 전역 budget ledger와 별개의 무결성 경계입니다.
- Narrative: `GameService`가 소유권/turn/action/rate limit을 확인하고 budget guard와 provider attempt 시작 경계를 통과한 호출만 Gemini telemetry로 감쌉니다.
- Gemini adapter: model/thinking compatibility와 provider usage metadata 관측만 소유하며 canonical game state를 변경하지 않습니다.
- Image: `ImageAssetService`가 asset 소유권과 기존 생성 결과를 먼저 확인하고 미생성 asset에 대해서만 rate limit과 provider 호출을 수행합니다.
- DB에 저장된 이미지 재조회는 provider를 호출하지 않으므로 Image quota와 global budget unit을 소비하지 않습니다.
