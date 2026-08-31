# UCTale

> 사용자가 만든 세계와 캐릭터에서 시작해, 플레이어의 선택이 하나의 이야기로 이어지는 AI 텍스트 어드벤처

<img alt="UCTale 프로젝트 로고" src="./docs/images/project_logo.png" width="600"/>

UCTale은 사용자가 직접 입력한 세계관과 주인공 설정을 바탕으로 이야기를 생성하는 인터랙티브 텍스트 어드벤처입니다. 플레이어는 서버가 발급한 선택 행동 가운데 하나를 고르고, 선택 결과에 따라 다음 장면과 선택지가 이어집니다. 장면에 시각적으로 표현할 요소가 있으면 서버가 관리하는 image asset을 통해 삽화도 제공합니다.

[서비스 바로가기](https://uctale.vercel.app/)

---

## 현재 방향

UCTale의 핵심 원칙은 **게임의 결정적 사실과 규칙은 서버가 소유하고, LLM은 확정된 결과를 서술한다**는 것입니다.

현재 main은 공유 베타 운영 안전망과 신뢰 가능한 턴 저장 기반의 구현 범위를 갖추었고, M3에서 서버 주도 행동 판정과 Skill Check를 확장하는 단계입니다.

- 서버: 세션 소유권, 현재 턴, idempotency, reservation lease, canonical state, GameLog, provider attempt 상한을 검증합니다.
- Narrative AI: 서버가 전달한 문맥을 바탕으로 이야기와 다음 선택지 표현을 생성합니다.
- Image AI: 브라우저의 임의 prompt가 아니라 서버가 발급한 image asset 계약만 실행합니다.
- Frontend: 서버가 반환한 선택 행동과 상태를 표시하고, 규칙을 재계산하지 않습니다.

---

## 게임 화면

### 게임 시작

| 세계관 설정 | 주인공 설정 |
| :---: | :---: |
| <img src="./docs/images/world.png" width="300"> | <img src="./docs/images/character.png" width="300"> |
| 플레이하고 싶은 세계와 분위기를 직접 입력합니다. | 이야기의 주인공이 될 캐릭터를 직접 설정합니다. |

### 이야기 진행

| 생성 이미지 예시 |
| :---: |
| <img src="./docs/images/monster.png" width="400"> |
| Pollinations를 사용한 UCTale charcoal 계열 이미지 |

---

## 플레이 방식

1. 공유 베타 접근 비밀번호로 단기 접근 세션을 발급받습니다.
2. 원하는 세계관과 주인공을 입력합니다.
3. 서버가 첫 장면과 서버 발급 선택 행동을 생성합니다.
4. 플레이어가 현재 턴에 유효한 행동을 선택합니다.
5. 서버가 소유권, expected turn, idempotency, action payload를 검증합니다.
6. 필요한 경우 Narrative provider를 호출하고 canonical state와 committed-turn log를 원자적으로 저장합니다.
7. 시각적으로 표현할 장면이 있으면 서버가 발급한 image asset을 통해 삽화를 제공합니다.

---

## 현재 구현된 내용

### 공유 베타 접근 제어와 세션 소유권

- 비밀번호 검증 성공 시 서버가 서명한 단기 접근 세션을 HttpOnly 쿠키로 발급합니다.
- 별도의 장기 owner key로 게임 세션 소유권을 서버와 PostgreSQL에서 관리합니다.
- 다른 owner는 session ID를 알아도 해당 세션을 조회하거나 진행할 수 없습니다.
- 게임 시작, 턴 진행, image asset API는 유효한 접근 세션이 있어야 호출할 수 있습니다.
- 운영 CORS origin은 명시적으로 관리하며 기본 production origin은 `https://uctale.vercel.app`입니다.
- 공유 비밀번호 인증 실패는 별도의 IP 기반 rate limit으로 보호합니다.

### 서버 발급 행동 경계

M3의 첫 선행 작업인 #33이 main에 반영되어 `AvailableAction`과 `PlayerAction` 경계가 존재합니다.

- 서버는 각 선택지에 action token/type/source turn/arguments를 발급할 수 있습니다.
- `/progress`는 현재 turn의 서버 발급 action과 요청 payload가 일치하는지 검증합니다.
- 변조되거나 만료된 action은 provider 호출 전에 거절됩니다.
- 기존 `choiceId` 기반 요청은 compatibility 경로로 유지합니다.
- 실제 전투, 아이템 사용, Skill Check 같은 규칙 행동은 후속 M3 작업에서 이 경계 위에 추가합니다.

### GameState와 Story Memory

서버는 세션별 canonical `GameState`를 JSON snapshot으로 저장하고 Narrative Engine에는 필요한 문맥만 전달합니다.

- `canonicalFacts`: 서버가 유지하는 장기 사실
- `rollingSummary`: 오래된 진행 내용을 제한된 크기로 압축한 기록
- `recentTurns`: 최근 진행 기록

snapshot JSON은 schema/ruleset version을 가지며 legacy production snapshot을 deterministic upgrader로 읽을 수 있습니다. read-time upgrade는 in-memory에서만 수행하고 다음 정상 canonical write에서 최신 형식으로 저장합니다.

### 신뢰 가능한 턴 파이프라인

M2의 턴 무결성·복구 구현 범위와 완료 조건은 main에 반영되었습니다.

- `Idempotency-Key`로 `/init`과 `/progress` mutation retry를 식별합니다.
- 같은 key + 같은 payload의 완료 요청은 provider를 재호출하지 않고 canonical 결과를 replay합니다.
- 같은 key를 다른 payload/operation에 재사용하면 provider 호출 전에 conflict로 거절합니다.
- `(session_id, expected_turn)` reservation lease로 유효 lease 동안 중복 provider 진입을 억제합니다.
- provider attempt는 reservation 획득 횟수와 분리된 `provider_attempt_count`로 관리하며 turn당 최대 3회로 제한합니다.
- validation, rate limit, budget guard 같은 pre-provider 실패는 provider attempt를 소비하지 않습니다.
- stale lease owner는 takeover 이후 canonical commit을 수행할 수 없습니다.
- `GameLog`는 append-only committed-turn ledger이고 `GameStateSnapshot`은 최신 상태 복구용 materialized snapshot입니다.
- `GameSession.currentTurn`, GameLog state version, snapshot, mutation result는 한 canonical commit으로 수렴합니다.
- 외부 provider strict exactly-once는 보장하지 않지만 canonical DB commit exactly-once와 bounded provider retry를 보장합니다.

### 이미지 생성

- Pollinations를 통해 게임 장면 이미지를 생성합니다.
- 브라우저는 provider prompt나 secret을 전달받지 않고 서버 발급 asset URL만 사용합니다.
- 동일 asset은 저장된 model, prompt, size, seed, safe, style version을 재사용합니다.
- 기본 정책은 `flux`, `768x432`, `uctale-charcoal-v2`입니다.
- provider 응답은 JPEG/PNG, 최대 8 MiB 계약을 검증합니다.
- provider 오류가 발생해도 canonical game turn은 유지됩니다.

### 비용 보호와 관측성

- Narrative/Image 비용 API는 owner/IP/session 기준의 in-process rate limit을 적용합니다.
- PostgreSQL `provider_usage_event` ledger로 일/월 provider 사용량을 누적합니다.
- warning/critical budget threshold와 선택적 `FAIL_CLOSED` 정책을 지원합니다.
- provider 호출마다 provider, model, operation, session, turn, request ID, latency, outcome, retry/attempt 수를 구조화 로그로 기록합니다.
- prompt/응답 전문, 비밀번호, API key, access/owner token은 관측 로그에 남기지 않습니다.

### 프론트엔드 UX

- Charcoal Folio 기반 narrative-first single-column UI를 사용합니다.
- React 19, Vite 8, plain CSS, SUIT Variable을 사용합니다.
- `system | light | dark` 테마를 지원합니다.
- API 오류는 화면 문맥 안에서 표시하고 가능한 경우 retry를 제공합니다.
- 진행 중 중복 요청을 막고 typewriter skip 및 `prefers-reduced-motion`을 지원합니다.
- image loading/failure, keyboard focus 이동, live-region 등 공유 베타 accessibility behavior를 포함합니다.
- Vercel Web Analytics를 통해 production page view를 확인할 수 있습니다.

---

## 기술 스택

| 영역 | 기술 |
| :--- | :--- |
| Frontend | React 19, React Router 7, Vite 8, Axios, plain CSS |
| Backend | Java 21, Spring Boot 4.1, Spring Web MVC, Spring Data JPA |
| Database | PostgreSQL, Flyway |
| Test Database | H2 + Testcontainers PostgreSQL 17.6 |
| Narrative AI | Google Gemini 2.5 Flash |
| Image AI | Pollinations |
| Deployment | Vercel, Render |

---

## 프로젝트 구조

```text
uctale/
├── frontend/                  # React 프론트엔드
├── src/main/java/            # Spring Boot 백엔드
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/          # Flyway migration
├── src/test/                 # unit/H2/PostgreSQL integration tests
├── docs/                     # architecture, testing, operations, benchmark 문서
├── scripts/                  # benchmark/production smoke 도구
├── build.gradle
└── README.md
```

주요 설계 문서:

- [개발 원칙](./CONTRIBUTING.md)
- [GameState / Story Memory](./docs/architecture/game-state-story-memory.md)
- [Game mutation idempotency](./docs/architecture/game-mutation-idempotency.md)
- [Turn reservation lease](./docs/architecture/game-turn-reservation.md)
- [Committed-turn GameLog](./docs/architecture/game-log-ledger.md)
- [Snapshot evolution](./docs/architecture/game-state-snapshot-evolution.md)
- [비용 보호](./docs/architecture/cost-controls.md)
- [이미지 생성](./docs/architecture/image-generation.md)
- [PostgreSQL integration tests](./docs/testing/postgresql-integration-tests.md)
- [Production smoke](./docs/operations/production-smoke.md)

---

## 로컬 실행

### 필요한 환경

- Java 21
- Node.js 22 권장
- PostgreSQL
- Docker-compatible container runtime: PostgreSQL integration test 실행 시 필요
- Google AI API Key
- Pollinations Token

### 백엔드 환경 변수

```env
GOOGLE_AI_API_KEY=...
POLLINATIONS_TOKEN=...
GAME_ACCESS_PASSWORD=...
GAME_ACCESS_SESSION_SECRET=32자 이상의 충분히 긴 임의 문자열
GAME_ACCESS_COOKIE_SECURE=false
GAME_CORS_ALLOWED_ORIGINS=http://localhost:5173
DATABASE_URL=jdbc:postgresql://localhost:5432/uctale
DATABASE_USERNAME=...
DATABASE_PASSWORD=...
```

세부 정책 환경변수는 `src/main/resources/application.properties`와 관련 architecture 문서를 기준으로 관리합니다.

### 백엔드 실행

```bash
./gradlew bootRun
```

Windows:

```powershell
.\gradlew.bat bootRun
```

### 프론트엔드 실행

```bash
cd frontend
npm ci
npm run dev
```

기본 개발 API는 `http://localhost:8080/api/game`입니다. 다른 API 서버를 사용할 경우 `VITE_API_URL`을 설정합니다.

---

## 테스트와 빌드

Backend unit/H2 suite:

```bash
./gradlew clean test
```

PostgreSQL integration suite:

```bash
./gradlew postgresIntegrationTest
```

Backend build:

```bash
./gradlew build
```

Frontend:

```bash
cd frontend
npm ci
npm test
npm run lint
npm run build
```

GitHub Actions CI도 backend unit test → PostgreSQL integration test → backend build 순서와 frontend test/lint/build를 실행합니다.

---

## 개발 현황

### M1 — 공유 베타 운영 안전망

구현 범위 완료.

- 공유 접근 세션과 owner 기반 세션 소유권
- API/CORS/Secret 경계
- image asset 보안
- rate limit과 provider 관측성
- Charcoal Folio UI 및 accessibility 마감
- production post-deploy smoke 자동화

### M2 — 신뢰 가능한 턴 파이프라인

구현 범위 완료.

- PostgreSQL integration test harness
- append-only committed-turn `GameLog`
- snapshot schema/ruleset version과 upgrader
- `/init`·`/progress` idempotency
- turn reservation/lease와 stale-owner fencing
- PostgreSQL 동시성·migration·crash regression matrix
- global provider budget guard
- provider attempt accounting과 pre-provider failure 회귀 수정

### M3 — 서버 주도 행동 판정

진행 중.

현재 #33 `AvailableAction` / `PlayerAction` 경계는 main에 반영되었습니다. 다음 핵심 작업은 타입 안전한 캐릭터 능력치와 pure Skill Check(#7), `ActionResolver` / `GameResult` 경계(#34), 확정 결과 기반 NarrativeContext(#36), 실제 turn 통합(#37) 순으로 확장하는 것입니다.

아직 구현되지 않은 전투·인벤토리·퀘스트·NPC 관계 기능은 현재 기능처럼 문서에 표시하지 않습니다.
