# Quest / Objective / World·Event Flag 경계

## 목적

장기 세션의 quest 진행과 world/event 조건은 story prose나 rolling summary가 아니라 canonical `GameState`가 소유한다. Narrative provider는 서버가 확정한 상태를 서술할 뿐 quest 완료·실패, objective progress, flag를 직접 만들거나 변경하지 않는다.

## 모델

- `QuestDefinition`: 안정적인 definition ID와 objective definition 집합
- `QuestRuntimeState`: `AVAILABLE`, `ACTIVE`, `COMPLETED`, `FAILED`와 objective progress
- `ObjectiveProgress`: `COUNT`, `BOOLEAN`, `STATE_MATCH`의 타입 안전한 최소 union
- `WorldFlag`, `EventFlag`: namespace가 concrete type으로 고정된 key/value/version 상태
- `QuestState`: quest runtime과 두 flag namespace를 분리해 보관

현재 최소 fixture `fixture.pathfinder`는 규칙 경계 검증을 위해 collection/dialogue/combat objective를 하나씩 제공한다. 대형 quest tree, 범용 DSL, procedural campaign은 이 경계에 포함하지 않는다.

## 전이 규칙

`QuestRules`는 이미 서버가 확정한 action/result만 관찰한다.

- collection: `ItemAcquired` 또는 quantity 증가 audit에서 target item definition의 증가량을 count한다.
- dialogue: `NARRATIVE_CHOICE`의 typed `choiceId` argument가 definition 조건과 일치할 때 boolean objective를 완료한다. display text는 조건 근거가 아니다.
- combat: `CombatEncounterChanged`의 canonical next status가 definition의 state-match 조건과 일치할 때 진행한다.

완료된 objective는 같은 결과를 다시 관찰해도 다시 진행하지 않는다. terminal quest(`COMPLETED`, `FAILED`)도 자동 progress 대상에서 제외된다. 따라서 canonical terminal transition/flag hook은 retry에서 중복 생성되지 않는다.

Quest status transition은 `AVAILABLE -> ACTIVE|FAILED`, `ACTIVE -> COMPLETED|FAILED`만 허용한다. terminal 상태에서 되돌아가는 전이는 거절한다.

## Flag 규칙

`WorldFlag`와 `EventFlag`는 동일 key를 공유할 수 없다. 새 flag의 version은 1이어야 하고, 변경 시 정확히 1 증가해야 한다. 동일 값을 새 version으로 다시 쓰는 전이는 허용하지 않는다.

현재 fixture는 collection objective 완료를 WorldFlag로, dialogue/combat 및 quest 완료를 EventFlag로 기록한다. 각 flag 변경 audit에는 namespace, key, previous/next value, version, 원인을 함께 남긴다.

## Activation과 legacy 호환성

Quest definition catalog가 존재한다고 해서 모든 세션에 quest를 자동 주입하지 않는다. 서버가 `QuestRules.activate`를 명시적으로 호출할 때만 known definition이 `AVAILABLE -> ACTIVE`로 전이되고 typed audit을 남긴다.

snapshot schema는 v8이다. v7 snapshot은 quest 의미가 없으므로 v7→v8 upgrade에서 `quests`, `worldFlags`, `eventFlags`를 모두 빈 map으로만 추가한다. 기존 `WorldState.flags`도 과거 의미를 추측해 typed flag로 승격하지 않는다.

현재 v8에서 `questState` 또는 세 하위 map이 누락되면 손상 snapshot으로 거절하며 암묵적으로 빈 값으로 복구하지 않는다.

## Persistence / recovery

`game_log.quest_changes_json`에는 다음 typed audit을 append-only로 저장한다.

- `QUEST_STATUS_CHANGED`
- `OBJECTIVE_PROGRESS_CHANGED`
- `FLAG_CHANGED`

`GameTurnCommit`은 previous `QuestState`에 audit을 replay한 결과가 next state와 정확히 같은지 저장 전에 검증한다. `GameStateRecovery`는 snapshot이 없을 때 ledger를 순서대로 replay한다.

Activation audit은 definition ID를 포함하므로 snapshotless recovery에서 known definition의 초기 objective progress를 결정적으로 재구성한 뒤 `ACTIVE`로 전이할 수 있다. 임의의 unknown definition이나 과거 prose에서 quest를 추측하지 않는다.

## NarrativeContext

`NarrativeContext.StateProjection`은 quest status/objective progress와 typed world/event flags를 read-only map으로 노출한다. provider guardrail은 prose만으로 quest/objective/flag를 변경하거나 서버가 확정한 transition을 다시 판정하는 것을 금지한다.

Provider 응답은 이 상태의 canonical 근거가 아니며 `StoryMemory` transcript가 quest 상태를 덮어쓰지 않는다.
