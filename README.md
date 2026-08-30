# UCTale

> 사용자가 만든 세계와 캐릭터를 바탕으로 이야기를 이어가는 AI 텍스트 어드벤처

<img alt="UCTale 프로젝트 로고" src="./docs/images/project_logo.png" width="600"/>

UCTale은 사용자가 직접 입력한 세계관과 주인공 설정을 바탕으로 이야기를 생성하는 텍스트 어드벤처입니다.

플레이어는 매 턴 제시되는 선택지 가운데 하나를 고르고, 선택에 따라 다음 장면과 선택지가 이어집니다. 장면에 시각적으로 보여줄 요소가 있을 때는 이야기와 함께 삽화도 생성합니다.

[서비스 바로가기](https://uctale.vercel.app/)

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
| Pollinations를 사용한 charcoal sketch 계열 이미지 |

---

## 플레이 방식

1. 공유 베타 접근 비밀번호를 확인해 단기 접근 세션을 발급받습니다.
2. 원하는 세계관과 주인공을 입력합니다.
3. 서버가 입력 내용을 바탕으로 첫 장면과 선택지를 생성합니다.
4. 플레이어가 선택지를 고르면 다음 턴이 진행됩니다.
5. 서버는 현재 턴과 게임 상태를 저장하고, 필요한 문맥만 Narrative Engine에 전달합니다.
6. 시각적으로 표현할 장면이 있으면 별도의 이미지 생성 경로를 통해 삽화를 제공합니다.

현재 플레이는 AI가 모든 게임 상태를 임의로 결정하는 방식이 아니라, 장기적으로 서버가 게임의 사실과 규칙을 소유하고 LLM은 그 결과를 이야기로 표현하는 구조를 목표로 개발하고 있습니다.

---

## 현재 구현된 내용

### 공유 베타 접근 제어

- 비밀번호 검증 성공 시 서버가 서명한 단기 접근 세션을 HttpOnly 쿠키로 발급합니다.
- 게임 시작, 턴 진행, 이미지 생성 API는 유효한 접근 세션이 있어야 호출할 수 있습니다.
- 운영 기본 CORS origin은 `https://uctale.vercel.app`만 허용하며, 허용 origin은 환경변수로 명시적으로 관리합니다.
- 접근 세션이 만료되거나 유효하지 않으면 프론트엔드가 재인증 화면으로 전환합니다.

### 이야기 생성과 선택지 진행

- 세계관과 캐릭터 설정을 입력해 새 게임 세션을 시작할 수 있습니다.
- Gemini가 현재 문맥을 바탕으로 이야기와 다음 선택지를 생성합니다.
- 클라이언트는 서버가 반환한 `sessionId`와 현재 턴을 기준으로 다음 진행 요청을 보냅니다.
- 오래된 턴이나 중복 진행 요청은 서버에서 충돌로 처리합니다.

### GameState와 Story Memory

게임의 장기 문맥을 단순히 전체 대화 기록으로 다시 전달하지 않습니다.

서버는 세션별 `GameState`를 저장하고, Narrative Engine에는 다음 정보를 구분해 전달합니다.

- `canonicalFacts`: 서버가 유지하는 장기 사실
- `rollingSummary`: 오래된 진행 내용을 압축한 기록
- `recentTurns`: 가장 최근 6턴

최신 상태는 `game_state_snapshot`에 JSON 스냅샷으로 저장하며, 세션과 턴 기록은 별도로 유지합니다.

현재 이 구조는 이후 능력치, 전투, 인벤토리, 퀘스트 같은 서버 주도 게임 규칙을 추가하기 위한 기반 단계입니다.

### 턴 저장과 무결성

- 세션마다 현재 턴 번호와 version을 관리합니다.
- 진행 요청은 `expectedTurn`을 포함합니다.
- 동일 세션에서 같은 턴이 중복 저장되지 않도록 DB 제약과 서버 검증을 함께 사용합니다.
- AI 호출이 실패하면 불완전한 다음 턴이 저장되지 않도록 처리합니다.

### 이미지 생성

- Pollinations를 통해 게임 장면에 사용할 이미지를 생성합니다.
- Provider 토큰은 서버 내부에서만 사용합니다.
- 클라이언트에는 Provider 비밀값 대신 서버 이미지 경로를 전달합니다.

---

## 기술 스택

| 영역 | 기술 |
| :--- | :--- |
| Frontend | React 19, React Router 7, Vite 8, Axios |
| Backend | Java 21, Spring Boot 4.1, Spring Web MVC, Spring Data JPA |
| Database | PostgreSQL, Flyway |
| Test Database | H2 |
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
│   └── db/                   # Flyway migration
├── src/test/                 # 백엔드 테스트
├── docs/                     # 설계 문서와 이미지
├── build.gradle
└── README.md
```

백엔드는 게임 진행 로직이 Gemini나 Pollinations의 구현 세부사항에 직접 의존하지 않도록 Provider 경계를 분리하고 있습니다.

핵심 방향은 다음과 같습니다.

- 게임의 결정적인 상태는 서버가 소유합니다.
- LLM은 확정된 문맥을 바탕으로 이야기를 생성합니다.
- 외부 AI Provider의 DTO와 호출 방식은 게임 도메인과 분리합니다.
- Provider 오류나 중복 요청이 저장된 게임 상태를 훼손하지 않도록 합니다.
- API Key와 토큰은 프론트엔드나 공개 응답에 포함하지 않습니다.

자세한 개발 원칙은 [CONTRIBUTING.md](./CONTRIBUTING.md)와 [GameState / Story Memory 설계 문서](./docs/architecture/game-state-story-memory.md)에서 확인할 수 있습니다.

---

## 로컬 실행

### 필요한 환경

- Java 21
- Node.js
- PostgreSQL
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

`GAME_ACCESS_COOKIE_SECURE=false`는 로컬 HTTP 개발용입니다. 운영에서는 기본값 `true`를 사용해 `Secure; SameSite=None` HttpOnly 쿠키를 발급합니다. 운영 CORS 기본값은 `https://uctale.vercel.app`이며, 다른 origin을 허용해야 할 때만 `GAME_CORS_ALLOWED_ORIGINS`를 쉼표로 구분해 명시합니다.

### 백엔드 실행

macOS / Linux:

```bash
./gradlew bootRun
```

Windows:

```powershell
.\gradlew.bat bootRun
```

기본 포트는 `8080`입니다.

### 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

로컬 백엔드를 사용할 경우 별도 설정 없이 `http://localhost:8080/api/game`을 사용합니다.

다른 API 서버를 사용할 경우 `frontend` 환경 변수에 다음 값을 설정합니다.

```env
VITE_API_URL=https://example.com/api/game
```

---

## 테스트와 빌드

백엔드:

```bash
./gradlew clean test
./gradlew build
```

프론트엔드:

```bash
cd frontend
npm ci
npm test
npm run lint
npm run build
```

---

## 개발 현황

현재는 기본적인 이야기 생성과 턴 진행에서 한 단계 더 나아가, 서버가 게임 규칙을 직접 관리할 수 있도록 내부 구조를 정리하고 있습니다.

진행 중인 주요 작업은 다음과 같습니다.

- 공유 베타 게임 세션 소유권
- AI 및 이미지 호출에 대한 비용 보호와 관측성
- 턴 idempotency와 동시 요청 처리 강화
- GameState snapshot 버전 관리
- 서버 주도 `AvailableAction`, `PlayerAction`, `GameResult` 도입
- 캐릭터 능력치와 Skill Check
- 이후 전투, 인벤토리, 퀘스트, NPC 관계 시스템 확장

아직 구현되지 않은 기능을 현재 기능처럼 문서에 표시하지 않고, 실제 코드와 완료된 작업을 기준으로 README를 갱신하는 것을 원칙으로 합니다.
