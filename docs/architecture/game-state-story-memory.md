# UCTale V2 GameState / Story Memory

## 목표

UCTale의 결정적 게임 상태는 서버가 소유하고, LLM은 그 상태와 서버가 확정한 결과를 서사로 표현하는 Narrative Engine으로 동작합니다.

## canonical GameState

`GameState`는 한 세션의 복원 가능한 현재 상태입니다.

- `turnNumber`: 현재 적용된 턴 번호
- `PlayerCharacter`: 설명과 서버 소유 `CharacterStats`
- `WorldState`: 세계의 서버 소유 상태와 flags 확장 지점
- `StoryMemory`: 장기 서사를 위한 제한된 내러티브 문맥

운영 DB에는 `game_state_snapshot` JSON snapshot으로 저장합니다. `game_session`과 append-only `game_log`는 세션/턴 무결성과 committed-turn 원장 역할을 담당합니다.

## CharacterStats와 Skill Check

#7 이후 `PlayerCharacter.stats`는 임의 문자열 Map이 아니라 다음 canonical stat을 가진 immutable `CharacterStats`입니다.

- `MIGHT` — 근력
- `AGILITY` — 민첩
- `INTELLECT` — 지능
- `WILL` — 의지
- `PRESENCE` — 매력

표시명은 frontend/UI 책임이며 canonical enum 이름과 분리합니다.

현재 최소 규칙:

- 신규/legacy 기본 stat score: `10`
- stat score 허용 범위: `1~30`
- stat modifier: `floor((score - 10) / 2)`
- d20 roll: `1~20`
- DC 허용 범위: `1~40`
- situational modifier: `-20~20`
- 판정: `rawRoll + statModifier + situationalModifier >= DC`
- natural 1/20 특수 성공·실패 규칙 없음
- Skill Check ruleset version: `1`

`SkillCheckResult`에는 stat type, raw roll, stat modifier, situational modifier, DC, total, outcome, ruleset version을 모두 기록해 후속 감사 저장에 필요한 계산 근거를 보존합니다.

랜덤은 `RandomSource` port로 분리합니다. production adapter는 `SecureRandom`을 사용하고 테스트는 fixed/sequence source로 결정적으로 검증합니다. 이 pure domain 판정은 Narrative provider를 호출하지 않습니다.

#7 범위에서는 이 규칙을 실제 `/progress` turn pipeline과 DB roll ledger에 아직 연결하지 않습니다. Skill Check의 실제 통합은 #37/#38에서 수행합니다.

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

Narrative Engine에는 전체 `game_log` 대신 read-only memory projection과 현재 요청에 필요한 canonical result/state 문맥만 전달합니다. Story Memory 자체의 canonical projection/token budget 재설계는 #46 범위입니다.

## 메모리 우선순위

충돌 시 다음 순서를 따릅니다.

`GameResult / canonical state > canonicalFacts > rollingSummary > recentTurns > LLM 생성 내용`

LLM 응답 자체는 canonical rule state 변경 권한이 없습니다.

## 현재 행동 경계

#33 이후 main에는 `AvailableAction`과 `PlayerAction` 경계가 있습니다.

- Narrative가 제안한 choice는 서버가 현재 turn에 속한 `AvailableAction`으로 발급합니다.
- 다음 `/progress`에서 서버는 제출된 action이 현재 turn의 발급 action과 일치하는지 검증합니다.
- 만료되거나 변조된 action은 Narrative provider 또는 규칙 실행 전에 거절합니다.
- 기존 choice ID는 compatibility adapter 경로로 유지합니다.

#34 이후 검증된 `PlayerAction`은 `ActionResolver`에서 `TurnResolution(GameResult, StateTransition)`으로 resolve됩니다. 현재 `ActionType`은 `NARRATIVE_CHOICE` 하나이므로 resolver registry는 두지 않습니다.

#36 이후 Narrative provider에는 raw state/action 문자열 조합 대신 확정된 `GameResult`와 canonical next-state에서 만든 provider-safe `NarrativeContext`를 전달합니다. 서버 발급 `PlayerAction.token`은 provider context에 포함하지 않습니다.

## 현재 턴 처리 흐름

1. idempotency와 `(session_id, expected_turn)` reservation을 확인합니다.
2. `expectedTurn`으로 현재 세션과 `GameState.turnNumber`를 검증합니다.
3. 제출된 `PlayerAction`을 현재 서버 발급 `AvailableAction`과 대조합니다.
4. `ActionResolver`가 `GameResult`와 canonical next `StateTransition`을 pure domain operation으로 확정합니다.
5. 확정 결과와 canonical next state에서 provider-safe `NarrativeContext`를 구성합니다.
6. rate limit과 provider budget/attempt 경계를 확인합니다.
7. Narrative Engine이 확정 결과를 재판정하지 않고 story와 다음 choice 후보를 생성합니다.
8. 검증된 story prose는 확정된 rule transition을 변경하지 않고 `StoryMemory` transcript에만 부착합니다.
9. 서버가 다음 server-issued available actions를 구성합니다.
10. `GameTurnCommit`이 `StateTransition`, `canonicalResultId`, `generatedStoryId`를 소유한 채 canonical transaction에서 저장됩니다.

세부 책임과 provider/transaction 경계는 [action-resolution.md](./action-resolution.md), prompt 계약과 retry/linkage 정책은 [narrative-context.md](./narrative-context.md)를 기준으로 합니다.

#37은 이 경계 위에 실제 Skill Check vertical slice와 roll 결과를 연결합니다.

## 기존 세션 호환성

현재 snapshot schema는 v2입니다. #31 이전 raw `GameState`는 logical v0, typed stats 이전 envelope는 v1로 읽어 deterministic upgrader에서 v2로 승격합니다.

legacy stats가 없으면 기본값 10을 적용하고 canonical legacy stat 값이 있으면 검증 후 보존합니다. read 자체는 DB를 다시 쓰지 않고 다음 정상 canonical commit에서 최신 snapshot 형식으로 저장합니다.

`GameState.advance(playerAction, storyText)`는 legacy `GameLog` recovery에서 동일한 StoryMemory를 복원해야 하므로 compatibility method로 유지합니다. 새 turn pipeline은 규칙 단계의 `advanceTurn()`과 provider 이후의 `recordNarrativeTurn()`을 분리해 사용합니다.

#36의 `game_log.canonical_result_id`와 `generated_story_id`는 legacy/opening 행에서 둘 다 `NULL`일 수 있도록 migration했습니다. 새 production progress commit은 두 linkage ID를 함께 기록하며 한쪽만 존재하는 상태는 DB CHECK constraint와 `GameTurnCommit`에서 거부합니다.

세부 snapshot 내용은 [game-state-snapshot-evolution.md](./game-state-snapshot-evolution.md)를 기준으로 합니다.
