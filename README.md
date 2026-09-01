# UCTale

> 사용자가 만든 세계와 캐릭터에서 시작해, 플레이어의 선택이 하나의 이야기로 이어지는 AI 텍스트 어드벤처

<img alt="UCTale 프로젝트 로고" src="./docs/images/project_logo.png" width="600"/>

UCTale은 사용자가 직접 입력한 세계관과 주인공 설정을 바탕으로 이야기를 생성하는 인터랙티브 텍스트 어드벤처입니다. 플레이어는 서버가 발급한 선택 행동 가운데 하나를 고르고, 선택 결과에 따라 다음 장면과 선택지가 이어집니다. 장면에 시각적으로 표현할 요소가 있으면 서버가 관리하는 image asset을 통해 삽화도 제공합니다.

[서비스 바로가기](https://uctale.vercel.app/)

---

## 현재 방향

UCTale의 핵심 원칙은 **게임의 결정적 사실과 규칙은 서버가 소유하고, LLM은 확정된 결과를 서술한다**는 것입니다.

현재 main은 공유 베타 운영 안전망과 신뢰 가능한 턴 저장 기반에 더해 `ActionResolver` / `GameResult` / provider-safe `NarrativeContext` 경계와 첫 실제 Skill Check turn vertical slice까지 갖추는 단계입니다.

- 서버: 세션 소유권, 현재 턴, idempotency, reservation lease, canonical state, Skill Check 판정, `GameResult`, GameLog, provider attempt 상한을 검증합니다.
- Narrative AI: 서버가 확정한 결과와 제한된 state/memory projection을 바탕으로 이야기와 다음 선택지 표현을 생성합니다.
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
6. Skill Check action이면 reservation 소유 요청이 서버 난수와 modifier/DC/outcome을 한 번 확정·보존합니다.
7. 서버가 action을 `GameResult` / canonical next state로 resolve하고 provider-safe `NarrativeContext`를 구성한 뒤 Narrative provider를 호출합니다.
8. 검증된 story는 확정 rule state를 변경하지 않고 transcript를 완성하며 canonical state와 committed-turn log를 원자적으로 저장합니다.
9. 시각적으로 표현할 장면이 있으면 서버가 발급한 image asset을 통해 삽화를 제공합니다.

---

## 현재 구현된 내용

### 공유 베타 접근 제어와 세션 소유권

- 비밀번호 검증 성공 시 서버가 서명한 단기 접근 세션을 HttpOnly 쿠키로 발급합니다.
- 별도의 장기 owner key로 게임 세션 소유권을 서버와 PostgreSQL에서 관리합니다.
- 다른 owner는 session ID를 알아도 해당 세션을 조회하거나 진행할 수 없습니다.
- 게임 시작, 턴 진행, image asset API는 유효한 접근 세션이 있어야 호출할 수 있습니다.
- 운영 CORS origin은 명시적으로 관리하며 기본 production origin은 `https://uctale.vercel.app`입니다.
- 공유 비밀번호 인증 실패는 별도의 IP 기반 rate limit으로 보호합니다.

### 서버 발급 행동과 Action Resolution 경계

#33의 `AvailableAction` / `PlayerAction`과 #34의 `ActionResolver` / `TurnResolution` / `GameResult` 경계가 main에 반영되어 있습니다.

- 서버는 각 선택지에 action token/type/source turn/arguments를 발급할 수 있습니다.
- 신규 typed 선택은 첫 vertical slice로 `SKILL_CHECK` action을 사용하며 현재 서버 정책은 `WILL`, DC 10, 상황 modifier 0입니다.
- `/progress`는 현재 turn의 서버 발급 action과 요청 payload가 일치하는지 검증합니다.
- 변조되거나 만료된 action은 provider 호출 전에 거절됩니다.
- metadata 없는 legacy wire 요청은 기존 `NARRATIVE_CHOICE` compatibility 의미를 유지합니다.
- 검증된 action은 provider 호출 전에 `ActionResolver`에서 `GameResult`와 canonical next `StateTransition`으로 확정됩니다.
- `GameService`는 action type별 규칙 세부 구현을 알지 않고 orchestration만 담당합니다.

### 타입 안전한 능력치와 Skill Check

#7의 순수 규칙 기반에 #37의 실제 turn 통합을 연결합니다.

- `StatType`: `MIGHT`, `AGILITY`, `INTELLECT`, `WILL`, `PRESENCE`
- 신규·legacy 캐릭터는 기본 능력치 10을 사용합니다.
- `CharacterStats`는 1~30 범위를 검증하고 `floor((score - 10) / 2)` modifier를 계산합니다.
- `Difficulty`, `DiceRoll`, situational modifier가 각 허용 범위를 검증합니다.
- `SkillCheck`는 `rawRoll + statModifier + situationalModifier >= DC`만으로 성공/실패를 결정합니다.
- natural 1/20 특수 규칙은 사용하지 않습니다.
- `SkillCheckResult`는 raw roll, stat modifier, situational modifier, DC, total, outcome, ruleset version을 보존합니다.
- production random adapter는 `SecureRandom`, 테스트는 fixed/sequence `RandomSource`를 사용합니다.
- 같은 idempotency mutation retry는 reservation에 보존된 동일 판정을 재사용하고, 다른 request가 만료 lease를 takeover하면 새 판정을 확정합니다.
- canonical commit은 Skill Check 결과와 state transition을 같은 transaction에서 `GameLog`에 기록합니다.
- frontend의 능력치/판정 결과 표시는 #38 범위입니다.

### GameState와 Story Memory

서버는 세션별 canonical `GameState`를 JSON snapshot으로 저장하고 Narrative Engine에는 필요한 projection만 전달합니다.

- `PlayerCharacter`는 typed `CharacterStats`를 소유합니다.
- `canonicalFacts`: 서버가 유지하는 장기 사실
- `rollingSummary`: 오래된 진행 내용을 제한된 크기로 압축한 기록
- `recentTurns`: 최근 진행 기록

snapshot JSON은 schema/ruleset version을 가지며 legacy production snapshot을 deterministic upgrader로 읽을 수 있습니다. typed stats 도입으로 현재 snapshot schema는 v2이며, v0/v1 stats는 읽을 때 순수 변환합니다. read-time upgrade는 in-memory에서만 수행하고 다음 정상 canonical write에서 최신 형식으로 저장합니다.

### GameResult 기반 NarrativeContext

#36 이후 progress Narrative provider에는 raw `GameState + 사용자 행동 문자열` 조합 대신 서버가 확정한 결과를 provider-safe projection으로 전달합니다.

- `NarrativeContext`는 canonical result ID, resolved action projection, outcome, optional Skill Check projection, canonical facts/events/state changes, canonical next-state projection, memory projection, narrative cues를 포함합니다.
- Skill Check projection은 raw roll, stat/situational modifier, DC, total, success/failure, ruleset version을 포함합니다.
- 서버 발급 `PlayerAction.token`은 provider context에 포함하지 않습니다.
- prompt는 확정 결과/state, narrative cues, 금지 canonical mutation을 분리합니다.
- provider는 story prose와 다음 choice 후보를 만들 수 있지만 서버가 확정한 outcome/roll/state change를 재판정할 수 없습니다.
- provider story는 canonical state를 직접 변경하지 않고 기존 StoryMemory transcript만 완성합니다.
- `game_log`는 progress turn의 `canonical_result_id` / `generated_story_id`와 선택적 Skill Check audit 필드를 기록합니다. legacy/opening 행은 Skill Check 필드가 모두 비어 있을 수 있습니다.
- provider failure 시 canonical turn은 진행하지 않으며 같은 idempotent 요청은 reservation에 저장된 Skill Check 판정과 동일 canonical result link를 재사용합니다.

Gemini structured output의 provider-specific schema validation과 bounded repair/retry 강화는 #35의 별도 범위입니다. Story Memory projection/token budget 전면 개선은 #46 범위입니다.

### 신뢰 가능한 턴 파이프라인

M2의 턴 무결성·복구 구현 범위와 완료 조건은 main에 반영되었습니다.

- `Idempotency-Key`로 `/init`과 `/progress` mutation retry를 식별합니다.
- 같은 key + 같은 payload의 완료 요청은 provider를 재호출하지 않고 canonical 결과를 replay합니다.
- 같은 key를 다른 payload/operation에 재사용하면 provider 호출 전에 conflict로 거절합니다.
- `(session_id, expected_turn)` reservation lease로 유효 lease 동안 중복 provider 진입을 억제합니다.
- Skill Check가 필요한 typed action은 provider 호출 전에 현재 reservation owner가 판정을 한 번 저장합니다.
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
- [Action Resolution](./docs/architecture/action-resolution.md)
- [Skill Check turn integration](./docs/architecture/skill-check-turn.md)
- [GameResult 기반 NarrativeContext](./docs/architecture/narrative-context.md)
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

현재 #33 `AvailableAction` / `PlayerAction`, #34 `ActionResolver` / `GameResult`, #36 확정 결과 기반 `NarrativeContext`, #7 typed `CharacterStats` / pure Skill Check 규칙에 더해 #37 Skill Check turn 통합과 감사 저장이 반영됩니다.

다음 UI 단계는 #38 능력치·Skill Check 결과 표시입니다. Gemini structured output 검증과 bounded recovery는 #35의 별도 P1 작업입니다.

아직 구현되지 않은 전투·인벤토리·퀘스트·NPC 관계 기능은 현재 기능처럼 문서에 표시하지 않습니다.
