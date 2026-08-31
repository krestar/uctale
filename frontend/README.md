# UCTale Frontend

UCTale의 공유 베타 웹 클라이언트입니다. 서버가 소유한 게임 상태와 서버 발급 action을 표시하고 전달하며, 브라우저에서 게임 규칙이나 provider prompt를 임의로 계산하지 않습니다.

## 기술 스택

- React 19
- React Router 7
- Vite 8
- Axios
- plain CSS
- SUIT Variable 2.0.5
- Vercel Web Analytics
- Node.js built-in test runner (`node --test`)

의존성의 정확한 버전 범위는 `package.json`과 lock file을 기준으로 합니다.

## UI 방향

현재 화면은 Charcoal Folio 기반의 narrative-first single-column 구성을 사용합니다.

- `AccessScreen`: 공유 베타 접근 인증
- `GameSetupScreen`: 세계관/주인공 입력과 게임 시작
- `GamePlayScreen`: 장면 이미지, story, 서버 발급 선택 행동 표시
- `ThemeSelector`: system/light/dark 테마
- `GameImage`: 인증된 image asset fetch와 loading/failure 처리
- `TypewriterText`: story typing, skip, reduced-motion 처리

디자인 계약은 `../docs/frontend/design-system.md`를 기준으로 합니다.

## 실행

Node.js 22 사용을 권장합니다.

```bash
npm ci
npm run dev
```

기본 개발 서버는 Vite가 안내하는 local URL을 사용합니다.

백엔드 기본 API는 `http://localhost:8080/api/game`이며 다른 서버를 사용할 때는 다음 환경변수를 설정합니다.

```env
VITE_API_URL=https://example.com/api/game
```

## 검증

```bash
npm ci
npm test
npm run lint
npm run build
```

GitHub Actions도 위 test/lint/build 경계를 검증합니다.

## 주요 디렉터리

```text
src/
├── api/            # API 호출과 오류 계약
├── components/     # BrandHeader, GameImage, ThemeSelector, TypewriterText
├── screens/        # Access / Setup / Play 화면
├── theme/          # theme 상태와 semantic token 연결
├── App.jsx         # auth/game orchestration
├── App.css         # 화면/컴포넌트 스타일
└── interaction.css # interaction/accessibility 상태 스타일
```

## 책임 경계

- access/owner token은 HttpOnly cookie로 관리하며 Web Storage에 저장하지 않습니다.
- `/progress`에는 서버가 반환한 action payload를 그대로 전달하며 성공/실패 판정을 frontend에서 재계산하지 않습니다.
- image provider prompt, provider URL, API secret은 frontend에 노출하지 않습니다.
- request 중 중복 진행을 막고, 실패 시 기존 화면 상태를 보존한 채 명시적 retry UX를 제공합니다.
