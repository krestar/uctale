# UCTale

> 사용자가 만든 세계와 캐릭터에서 시작해, 플레이어의 선택이 하나의 이야기로 이어지는 AI 텍스트 어드벤처

<img alt="UCTale 프로젝트 로고" src="./docs/images/project_logo.png" width="600"/>

UCTale은 사용자가 직접 입력한 세계관과 주인공 설정을 바탕으로 이야기를 생성하는 인터랙티브 텍스트 어드벤처입니다. 핵심 원칙은 **게임의 결정적 사실과 규칙은 서버가 소유하고, LLM은 서버가 확정한 결과를 서술한다**는 것입니다.

[서비스 바로가기](https://uctale.vercel.app/)

---

## 현재 방향

현재 `main`은 공유 베타 운영 안전망과 신뢰 가능한 턴 저장 기반 위에 서버 주도 action resolution을 확장하고 있습니다.

- 서버: 세션 소유권, 현재 turn, idempotency/reservation, Skill Check, Inventory/Equipment, HP/MP/Status Effect, Combat Encounter/Attack, Ability/MP/Cooldown, Quest/Objective/World·Event Flag와 canonical commit을 결정합니다.
- Narrative AI: provider-safe `NarrativeContext`로 전달된 서버 확정 결과와 read-only state/memory projection을 서술합니다. canonical state를 직접 변경하거나 판정을 다시 수행하지 않습니다.
- Image AI: 브라우저의 임의 prompt가 아니라 서버가 발급한 image asset 계약만 실행합니다.
- Frontend: 서버가 반환한 선택 행동과 projection을 표시하며 게임 규칙을 재계산하지 않습니다.

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

## 플레이 방식과 서버 경계

1. 공유 베타 접근 세션과 owner 기반 게임 세션을 검증합니다.
2. 플레이어가 현재 turn에 서버가 발급한 `AvailableAction`을 제출합니다.
3. 서버가 expected turn, idempotency, reservation lease와 action token/type/arguments를 검증합니다.
4. 필요한 Skill Check/Attack 난수 판정은 reservation owner가 provider 호출 전에 한 번 확정·보존합니다.
5. `ActionResolver`가 typed action/effect를 `GameResult`와 canonical next state로 resolve합니다.
6. `QuestRules`가 서버 확정 action/state change만 관찰해 quest/objective/flag 전이를 적용합니다.
7. provider-safe `NarrativeContext`를 구성하고 Narrative provider가 확정 결과를 story로 표현합니다.
8. `GameTurnCommit`이 canonical state, typed audit, narrative linkage를 한 transaction으로 저장합니다.
9. 시각적으로 표현할 장면이 있으면 서버 발급 image asset을 통해 삽화를 제공합니다.

외부 provider의 strict exactly-once는 보장하지 않습니다. 대신 canonical DB commit의 중복 적용을 막고 provider attempt를 bounded하게 관리합니다.

---

## 현재 구현된 게임 상태와 규칙

### Action Resolution / Skill Check

- `AvailableAction` / `PlayerAction`과 `ActionResolver` / `TurnResolution` / `GameResult` 경계
- `NARRATIVE_CHOICE`, `SKILL_CHECK`, `COMBAT_ATTACK`, `COMBAT_ABILITY`, `COMBAT_PASS`, `COMBAT_ESCAPE`
- typed `CharacterStats`: `MIGHT`, `AGILITY`, `INTELLECT`, `WILL`, `PRESENCE`
- production `SecureRandom`, deterministic test random source
- Skill Check와 Attack 판정의 reservation 기반 retry 재사용

### Inventory / Equipment / Vitals

- stable item definition ID와 owned stack/instance ID 분리
- `MAIN_HAND`, `OFF_HAND`, `BODY`, `ACCESSORY` equipment slot
- acquire/remove/quantity/consume/equip/unequip typed transition
- HP/MP와 status effect의 타입 안전한 상태 전이
- item attack/damage modifier가 서버 전투 판정에 반영
- typed GameLog audit과 snapshotless recovery

상점/거래, 랜덤 loot table, 강화/내구도, 상세 inventory UI는 현재 범위가 아닙니다.

### Combat / Ability

- `CombatEncounter`의 `PENDING`, `ACTIVE`, `RESOLVED`, `ESCAPED` lifecycle
- server-owned turn order/current actor와 `EnemyState`
- d20 attack, defense, damage reduction, equipment modifier, enemy HP 결과를 서버가 확정
- `COMBAT_ABILITY`의 MP 비용, target, damage/heal/status effect, cooldown을 원자적으로 적용
- cooldown은 성공적으로 완료된 turn에서 deterministic하게 감소
- attack/ability 결과와 combat state를 `game_log.combat_changes_json`에 audit

치명타/속성 상성, AoE, 전술 좌표, 다수 party, boss phase, 복잡한 AI와 대형 skill tree는 후속 범위입니다.

### Quest / Objective / World·Event Flag

- `QuestDefinition` / `QuestRuntimeState`와 `AVAILABLE`, `ACTIVE`, `COMPLETED`, `FAILED`
- `COUNT`, `BOOLEAN`, `STATE_MATCH` typed objective progress
- 최소 collection/dialogue/combat objective fixture
- typed `WorldFlag` / `EventFlag` key/value/version 규칙
- quest progress는 story prose가 아니라 서버 action/state change 결과로만 전이
- status/progress/flag previous/next와 cause를 `game_log.quest_changes_json`에 저장하고 recovery에서 replay
- `NarrativeContext`에는 read-only quest/objective/flag projection만 전달

범용 quest scripting DSL, editor/admin UI, procedural quest generation, 전체 branching campaign, 상세 quest journal UI는 현재 범위가 아닙니다.

### GameState / Snapshot / Recovery

현재 snapshot schema는 **v8**, ruleset version은 **1**입니다.

- v0: pre-envelope raw `GameState`
- v2: typed stats
- v3: Inventory/Equipment
- v4: HP/MP/Status Effect
- v5: Combat Encounter/EnemyState
- v6: item combat modifier / enemy combat profile
- v7: Ability cooldown state
- v8: Quest/Objective/World·Event Flag state

`GameStateUpgrader`는 지원되는 legacy schema를 한 단계씩 deterministic하게 승격합니다. 의미를 안전하게 복구할 수 없는 값은 추측하지 않으며, 현재 v8의 필수 필드 누락이나 손상 값은 조용히 기본값으로 복구하지 않습니다. 기존 `WorldState.flags`도 typed World/Event Flag로 임의 승격하지 않습니다.

`GameLog`는 append-only committed-turn ledger이고 snapshot은 최신 상태 복구용 materialized state입니다. snapshot이 없으면 inventory/vitals/combat/ability/quest audit을 순서대로 replay합니다.

### NarrativeContext / Provider

- raw `GameState + 사용자 문자열` 대신 서버 확정 `GameResult`와 canonical next-state projection 사용
- server-issued action token은 provider에 전달하지 않음
- Skill Check, combat/ability, inventory/vitals, quest/flag state change를 read-only projection으로 전달
- provider story는 canonical rule state를 변경하지 않고 StoryMemory transcript만 완성
- Gemini structured output schema와 adapter validation, bounded recovery 적용
- 기본 Narrative model: `gemini-3.7-flash`

Story Memory projection/token budget 전면 개선은 #46 범위입니다.

---

## 신뢰 가능한 턴 파이프라인과 운영 안전망

- `/init`, `/progress` mutation의 `Idempotency-Key`
- `(session_id, expected_turn)` reservation lease와 stale-owner fencing
- provider attempt와 reservation 획득 횟수 분리
- validation/rate limit/budget guard 같은 pre-provider 실패는 provider attempt를 소비하지 않음
- PostgreSQL append-only GameLog와 snapshot/recovery
- provider usage ledger, 일/월 budget guard와 구조화 관측 로그
- 공유 베타 접근 세션, owner 기반 세션 소유권, 명시적 CORS 정책
- server-issued image asset 계약과 production smoke workflow

---

## 프론트엔드

- React 19, React Router 7, Vite 8, plain CSS
- Charcoal Folio 기반 narrative-first UI
- `system | light | dark` theme
- 캐릭터 능력치와 Skill Check 결과 projection
- retry/error 상태, typewriter skip, `prefers-reduced-motion`, keyboard/live-region accessibility
- Vercel Web Analytics

---

## 기술 스택

| 영역 | 기술 |
| :--- | :--- |
| Frontend | React 19, React Router 7, Vite 8, Axios, plain CSS |
| Backend | Java 21, Spring Boot 4.1, Spring Web MVC, Spring Data JPA |
| Database | PostgreSQL, Flyway |
| Test Database | H2 + Testcontainers PostgreSQL 17.6 |
| Narrative AI | Google Gemini 3.7 Flash |
| Image AI | Pollinations |
| Deployment | Vercel, Render |

---

## 주요 설계 문서

- [개발 원칙](./CONTRIBUTING.md)
- [Action Resolution](./docs/architecture/action-resolution.md)
- [Skill Check turn integration](./docs/architecture/skill-check-turn.md)
- [Inventory / Equipment](./docs/architecture/inventory-equipment.md)
- [HP / MP / Status Effect](./docs/architecture/vitals-status-effects.md)
- [Combat Encounter / EnemyState](./docs/architecture/combat-encounter.md)
- [Ability / Resource / Cooldown](./docs/architecture/ability-cooldown.md)
- [Quest / Objective / World·Event Flag](./docs/architecture/quest-objective-flags.md)
- [GameResult 기반 NarrativeContext](./docs/architecture/narrative-context.md)
- [Gemini Narrative provider](./docs/architecture/gemini-narrative-provider.md)
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

필요 환경: Java 21, Node.js 22 권장, PostgreSQL, PostgreSQL integration test용 Docker-compatible container runtime, Google AI API Key, Pollinations Token.

백엔드 주요 환경 변수 예시:

```env
GOOGLE_AI_API_KEY=...
GOOGLE_AI_MODEL=gemini-3.7-flash
GOOGLE_AI_THINKING_OPENING=medium
GOOGLE_AI_THINKING_PROGRESS=low
POLLINATIONS_TOKEN=...
GAME_ACCESS_PASSWORD=...
GAME_ACCESS_SESSION_SECRET=32자 이상의 충분히 긴 임의 문자열
GAME_ACCESS_COOKIE_SECURE=false
GAME_CORS_ALLOWED_ORIGINS=http://localhost:5173
DATABASE_URL=jdbc:postgresql://localhost:5432/uctale
DATABASE_USERNAME=...
DATABASE_PASSWORD=...
```

```bash
./gradlew bootRun
```

```bash
cd frontend
npm ci
npm run dev
```

기본 개발 API는 `http://localhost:8080/api/game`입니다. 다른 API 서버를 사용할 경우 `VITE_API_URL`을 설정합니다.

---

## 테스트와 빌드

```bash
./gradlew clean test
./gradlew postgresIntegrationTest
./gradlew build
```

```bash
cd frontend
npm ci
npm test
npm run lint
npm run build
```

GitHub Actions CI도 backend unit test → PostgreSQL integration test → backend build와 frontend test/lint/build를 실행합니다.

---

## 개발 현황

### M1 — 공유 베타 운영 안전망

구현 범위 완료.

### M2 — 신뢰 가능한 턴 파이프라인

구현 범위 완료.

### M3 — 서버 주도 행동 판정

진행 중. 현재 main에는 server-issued action/action resolution, Skill Check, Inventory/Equipment, HP/MP/Status Effect, Combat Encounter/Attack, item combat modifier/enemy combat profile, Ability/MP/Cooldown, Quest/Objective/World·Event Flag와 provider-safe NarrativeContext가 반영되어 있습니다.

NPC 관계, 상점/거래·loot·강화, 복잡한 전투/캠페인 규칙 등은 아직 현재 기능으로 취급하지 않습니다.
