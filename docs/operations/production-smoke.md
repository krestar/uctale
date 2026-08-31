# Production post-deploy smoke

UCTale의 일반 CI는 테스트, lint, build를 검증하지만 실제 Vercel frontend와 Render backend 사이의 production CORS·credential 동작까지 보장하지는 않습니다.

`Production Smoke` workflow는 Vercel이 현재 `main` commit에 production deployment 성공 status를 기록한 뒤 별도의 GitHub Actions run으로 실행됩니다. 일반 CI와 run 이름을 분리해 production 회귀를 빠르게 식별합니다.

## 자동 검증 범위

자동 smoke는 외부 AI provider를 호출하지 않습니다.

1. `https://uctale.vercel.app/`이 HTTP 200을 반환하고 UCTale root markup을 제공하는지 확인합니다.
2. Render backend의 `/api/game/access-session`에 production origin으로 CORS preflight를 보내 다음 계약을 확인합니다.
   - `Access-Control-Allow-Origin: https://uctale.vercel.app`
   - `Access-Control-Allow-Credentials: true`
3. `/api/game/verify-password`로 공유 접근 세션을 발급받고 다음을 확인합니다.
   - HTTP 204
   - production CORS credential 계약
   - `uctale_access`, `uctale_owner` HttpOnly session cookie가 발급 가능한 상태
4. 발급된 cookie jar를 `/api/game/access-session`에 재전송해 실제 credential 검증 경로가 HTTP 204를 반환하는지 확인합니다.

이 흐름은 frontend 가용성, backend 가용성, CORS allowlist, cross-origin credential 발급·재사용을 production 구성 그대로 검증합니다.

## 비용 정책

자동 post-deploy smoke에서는 `/init`, `/progress`, image generation을 호출하지 않습니다. 이 경로들은 Gemini 또는 Pollinations provider 비용이 발생할 수 있으므로 코드 push마다 자동 반복하지 않습니다.

따라서 자동 smoke의 provider 호출 수는 항상 0회입니다. 실제 narrative 생성, 턴 진행, image asset 생성은 기능 변경 또는 M2 종료 검증 시 수동 smoke 대상으로 유지합니다.

## GitHub Actions secret

Repository Actions secret에 다음 값을 한 번 등록해야 합니다.

- `PRODUCTION_SMOKE_ACCESS_PASSWORD`: 현재 production `GAME_ACCESS_PASSWORD`와 같은 공유 베타 비밀번호

workflow와 스크립트는 secret 값을 출력하지 않습니다. 비밀번호 JSON과 cookie jar는 runner의 임시 디렉터리에만 만들고 종료 시 삭제합니다. response의 `Set-Cookie` header나 cookie 값도 로그에 출력하지 않습니다.

## 실행 조건

자동 실행:

- GitHub `status` event의 context가 `Vercel`
- status가 `success`
- status 대상 SHA가 실행 시점의 현재 `main` HEAD와 동일

오래된 deployment가 늦게 완료되어 status가 도착해도 현재 `main`과 SHA가 다르면 smoke를 건너뜁니다.

수동 실행:

- GitHub Actions의 `Production Smoke` workflow에서 `workflow_dispatch`로 실행할 수 있습니다.

## 실패 로그

스크립트는 단계와 HTTP status, 공개 origin 같은 진단 정보만 출력합니다. 비밀번호, access token, owner token, cookie header 전문은 출력하지 않습니다.

실패는 `Production Smoke`라는 독립 workflow run으로 표시되므로 일반 `CI` 실패와 구분됩니다.

## 수동 smoke로 남기는 범위

다음 항목은 #70 자동화 범위 밖에 둡니다.

- 실제 `/init` narrative 생성
- 실제 `/progress` 턴 진행
- 실제 image asset provider fetch
- 브라우저 UI interaction과 visual regression
- production 실패 시 자동 rollback

이 항목은 provider 비용 또는 브라우저 수준 검증이 필요합니다. 관련 기능을 변경했거나 milestone 종료 게이트를 확인할 때 수동으로 검증합니다.

## 로컬 실행

production과 같은 공유 비밀번호를 환경변수로 전달하면 같은 스크립트를 직접 실행할 수 있습니다.

```bash
UCTALE_SMOKE_ACCESS_PASSWORD='...' bash scripts/production-smoke.sh
```

공개 endpoint를 다른 환경에서 검증해야 할 때만 다음 환경변수를 덮어씁니다.

- `UCTALE_SMOKE_FRONTEND_URL`
- `UCTALE_SMOKE_BACKEND_URL`
- `UCTALE_SMOKE_ORIGIN`
