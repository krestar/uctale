# UCTale V2 GameState / Story Memory

## 목표

UCTale의 결정적 게임 상태는 서버가 소유하고, LLM은 그 상태와 서버가 확정한 결과를 서사로 표현하는 Narrative Engine으로 동작합니다.

## canonical GameState

`GameState`는 한 세션의 복원 가능한 현재 상태입니다.

- `turnNumber`: 현재 적용된 턴 번호
- `PlayerCharacter`: 플레이어의 서버 소유 상태. #7에서 typed stats와 Skill Check 기반을 확장합니다.
- `WorldState`: 세계의 서버 소유 상태와 flags 확장 지점
- `StoryMemory`: 장기 서사를 위한 제한된 내러티브 문맥

운영 DB에는 `game_state_snapshot` JSON snapshot으로 저장합니다. `game_session`과 append-only `game_log`는 세션/턴 무결성과 committed-turn 원장 역할을 담당합니다.

## Story Memory

Story Memory는 세 계층으로 구분합니다.

1. `canonicalFacts`
   - 서버가 진실로 승인한 장기 사실
   - 현재는 최초 세계관과 플레이어 설정으로 시작
   - 향후 Game Engine의 검증된 state transition만 canonical truth를 변경
2. `rollingSummary`
   - 최근 문맥에서 밀려난 오래된 턴의 제한된 압축 기록
   - 현재 별도 AI 호출 없이 서버가 결정적으로 누적
   - 최대 4,000자
3. `recentTurns`
   - 가장 최근 6턴
   - 플레이어 행동과 결과 본문 보관

Narrative Engine에는 전체 `game_log` 대신 위 계층과 현재 요청에 필요한 행동 문맥만 전달합니다.

## 메모리 우선순위

충돌 시 다음 순서를 따릅니다.

`canonicalFacts > rollingSummary > recentTurns > LLM 생성 내용`

LLM 응답 자체는 canonical state 변경 권한이 없습니다.

## 현재 행동 경계

#33 이후 main에는 `AvailableAction`과 `PlayerAction` 경계가 있습니다.

- Narrative가 제안한 choice는 서버가 현재 turn에 속한 `AvailableAction`으로 발급합니다.
- 응답에는 action token/type/source turn/arguments를 포함할 수 있습니다.
- 다음 `/progress`에서 서버는 제출된 action이 현재 turn의 발급 action과 일치하는지 검증합니다.
- 만료되거나 변조된 action은 Narrative provider 또는 향후 규칙 실행 전에 거절합니다.
- 기존 choice ID는 compatibility adapter 경로로 유지합니다.

현재 `ActionType`은 향후 규칙 행동을 위한 경계이며 Attack/UseItem/Skill Check의 실제 판정 규칙을 이미 구현했다는 의미는 아닙니다.

## 현재 턴 처리 흐름

1. idempotency와 `(session_id, expected_turn)` reservation을 확인합니다.
2. `expectedTurn`으로 현재 세션과 `GameState.turnNumber`를 검증합니다.
3. 제출된 `PlayerAction`을 현재 서버 발급 `AvailableAction`과 대조해 표시 텍스트를 확정합니다.
4. rate limit과 provider budget guard를 확인합니다.
5. 현재 단계에서는 `GameState`와 action 문맥으로 `NarrativeContext`를 구성합니다.
6. Narrative Engine이 이야기와 다음 choice 후보를 생성합니다.
7. 서버가 다음 `GameState`와 server-issued available actions를 구성합니다.
8. `game_log`, `game_session`, `game_state_snapshot`, mutation 완료 결과를 canonical transaction에서 저장합니다.
9. reservation owner, expected turn, optimistic lock/unique constraint가 어긋나면 commit을 거절합니다.

M3의 #34/#36 이후에는 **규칙 결과와 다음 state를 Narrative 호출 전에 확정**하는 구조로 이 흐름을 더 강화합니다.

## 기존 세션 호환성

현재 snapshot은 versioned envelope를 사용합니다. #31 이전 raw `GameState` snapshot은 logical schema v0로 식별해 deterministic upgrader로 읽습니다.

snapshot이 없는 session은 committed `GameLog`를 turn 순서로 읽어 `GameStateRecovery`가 상태를 복구합니다. legacy snapshot read 자체는 DB를 다시 쓰지 않고, 다음 정상 canonical commit에서 최신 snapshot 형식으로 저장합니다.

세부 내용은 [game-state-snapshot-evolution.md](./game-state-snapshot-evolution.md)를 기준으로 합니다.

## 향후 확장

다음 상태는 prompt 문자열이나 임시 service field가 아니라 `GameState`와 명시적 game rule로 확장합니다.

- typed character stats와 deterministic Skill Check
- inventory / equipment
- HP / status effects
- combat state
- XP / level / skills
- quest / world flags
- NPC relationships

## 아직 하지 않는 것

- AI가 상태 변경 command/proposal을 직접 canonical state에 적용하는 기능
- 전투 공식과 전체 encounter system
- 아이템/퀘스트 대규모 content catalog
- 복잡한 클래스/직업 트리
