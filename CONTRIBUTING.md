# Contributing to UCTale

UCTale은 LLM이 이야기를 생성하지만 게임의 결정적 상태는 서버가 소유하는 방향으로 발전합니다. 기능을 추가할 때는 이 원칙을 우선합니다.

## 작업 유형

- `fix`: 잘못된 동작을 수정합니다.
- `feat`: 플레이어가 사용할 수 있는 새 기능을 추가합니다.
- `refactor`: 외부 동작을 유지하면서 구조를 개선합니다.
- `test`: 테스트를 추가하거나 수정합니다.
- `ci`: GitHub Actions 등 자동화 파이프라인을 수정합니다.
- `docs`: 문서만 변경합니다.
- `chore`: 그 외 개발 환경과 유지보수 작업입니다.

커밋 메시지는 Conventional Commits 형식을 권장합니다.

```text
feat: add deterministic skill checks
fix: return explicit session id in game response
refactor: isolate narrative provider from game service
```

## 권장 작업 흐름

1. Bug / Feature / Refactor / Game Design Issue 중 적절한 유형으로 문제와 완료 조건을 정의합니다.
2. 큰 게임 시스템은 먼저 Game Design Issue에서 서버 규칙과 LLM 역할을 구분합니다.
3. 작은 브랜치를 만들고 한 가지 목적만 해결합니다.
4. 로컬 테스트와 lint/build를 통과시킵니다.
5. PR에서 관련 Issue를 `Closes #번호`로 연결합니다.
6. CI가 통과하고 PR 체크리스트를 확인한 뒤 main에 병합합니다.

## 브랜치 이름

```text
feat/skill-check
fix/session-id-contract
refactor/narrative-provider
chore/github-ci
```

## 로컬 검증

### Backend

```bash
./gradlew clean test
./gradlew build
```

### Frontend

```bash
cd frontend
npm ci
npm run lint
npm run build
```

## UCTale 설계 원칙

### 1. 서버가 게임의 사실을 결정합니다

HP, MP, 판정 성공/실패, 경험치, 아이템 획득, 퀘스트 상태처럼 재현 가능해야 하는 값은 서버의 규칙과 GameState가 결정합니다.

### 2. LLM은 Narrative Engine입니다

LLM은 확정된 게임 결과를 자연스러운 이야기로 표현합니다. LLM 출력 자체를 신뢰하여 결정적 게임 상태를 직접 변경하지 않습니다.

### 3. Provider와 게임 로직을 분리합니다

Gemini, Pollinations 등 외부 제공자의 DTO, URL, SDK 세부사항이 핵심 game domain으로 퍼지지 않도록 adapter 경계를 유지합니다.

### 4. 외부 호출 실패가 게임 상태를 오염시키지 않아야 합니다

AI/Image provider timeout, 잘못된 JSON, 중복 요청이 발생해도 한 턴이 두 번 적용되거나 불완전한 상태가 저장되지 않도록 설계합니다.

### 5. Secret을 클라이언트에 전달하지 않습니다

API Key, 토큰, 비밀번호는 코드, 로그, query string 기반 public URL, frontend bundle 또는 API response에 포함하지 않습니다.

## PR 크기

한 PR에는 가능하면 하나의 목적만 둡니다. 구조 변경과 대규모 기능 추가를 한 PR에 섞기보다 먼저 리팩터링 안전망을 만들고, 이후 기능 PR을 추가하는 방식을 선호합니다.
