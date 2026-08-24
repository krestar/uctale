# UCTale V2 GameState / Story Memory

## 목표

UCTale의 결정적 게임 상태는 서버가 소유하고, LLM은 그 상태를 서사로 표현하는 Narrative Engine으로만 동작한다.

## canonical GameState

`GameState`는 한 세션의 복원 가능한 현재 상태다.

- `turnNumber`: 현재 적용된 턴 번호
- `PlayerCharacter`: 플레이어의 서버 소유 상태. #7부터 stats를 확장한다.
- `WorldState`: 세계의 서버 소유 상태와 flags 확장 지점
- `StoryMemory`: 장기 서사를 위한 제한된 내러티브 문맥

운영 DB에는 `game_state_snapshot`에 JSON 스냅샷으로 저장한다. `game_session`과 `game_log`는 세션/턴 무결성과 원장 역할을 계속 담당한다.

## Story Memory

Story Memory는 세 계층으로 구분한다.

1. `canonicalFacts`
   - 서버가 진실로 승인한 장기 사실
   - 현재는 최초 세계관과 플레이어 설정으로 시작한다.
   - 향후 Game Engine의 검증된 command/proposal만 변경할 수 있다.
2. `rollingSummary`
   - 최근 문맥에서 밀려난 오래된 턴의 압축 기록
   - 현재는 별도 AI 호출 없이 서버가 결정적으로 누적한다.
   - 최대 4,000자로 제한한다.
3. `recentTurns`
   - 가장 최근 6턴
   - 플레이어 행동과 결과 본문만 보관한다.

Narrative Engine에는 전체 `game_log` 대신 위 세 계층과 이번 플레이어 행동을 전달한다.

## 메모리 우선순위

충돌 시 다음 순서를 따른다.

`canonicalFacts > rollingSummary > recentTurns > LLM 생성 내용`

LLM 응답 자체는 canonical state 변경 권한이 없다.

## 턴 처리 흐름

1. `expectedTurn`으로 현재 세션과 `GameState.turnNumber`를 검증한다.
2. 선택지 ID를 서버가 기존 choices에서 실제 행동 문자열로 해석한다.
3. `GameState`에서 `NarrativeContext`를 구성한다.
4. Narrative Engine이 이야기와 다음 선택지만 생성한다.
5. 서버가 다음 `GameState`를 결정적으로 구성한다.
6. `game_log`, `game_session`, `game_state_snapshot`을 같은 transaction에서 저장한다.
7. 낙관적 잠금/unique constraint 충돌 시 전체 저장을 롤백한다.

## 기존 세션 호환성

V2 snapshot 도입 전 생성된 세션은 `game_state_snapshot`이 없을 수 있다. 이 경우 저장된 `game_log`를 턴 순서로 replay하여 최소 GameState를 복구한다. 다음 정상 저장부터 snapshot이 생성된다.

## 향후 확장

#7 이후 다음 상태는 `GameState` 내부에서 확장한다.

- 캐릭터 stats와 deterministic Skill Check
- inventory / equipment
- HP / status effects
- combat state
- XP / level / skills
- quest flags
- relationships

이 기능들은 prompt 문자열이나 `GameService` 임시 필드가 아니라 서버 GameState에 구현한다.

## 아직 하지 않는 것

- AI가 상태 변경 command/proposal을 직접 적용하는 기능
- 세부 전투 공식
- 아이템 카탈로그
- 퀘스트 콘텐츠 대량 작성
- 복잡한 클래스/직업 트리
