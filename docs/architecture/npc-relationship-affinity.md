# NPC Relationship / Affinity

## 책임 경계

NPC 관계의 결정적 사실은 서버가 소유한다. Narrative provider는 canonical `RelationshipState`와 `GameResult.RelationshipChanged`를 서술할 수 있지만 affinity, stage, 변화 원인이나 NPC identity를 prose만으로 생성하거나 변경하지 않는다.

## Identity

NPC는 `NpcIdentity(definitionId, instanceId)`로 식별한다. `definitionId`는 NPC 종류/정의를, `instanceId`는 실제 세션 인스턴스를 나타내며 display name과 분리된다. 같은 `instanceId`에 다른 `definitionId`를 연결하면 거절한다.

## Affinity / Stage

Affinity는 `-100..100` 범위에서 clamp한다. Stage는 affinity에서 서버가 파생한다.

- `HOSTILE`: -100..-50
- `WARY`: -49..-20
- `NEUTRAL`: -19..19
- `FRIENDLY`: 20..49
- `TRUSTED`: 50..100

`RelationshipCommand`는 `TALK`, `QUEST`, `GAME_RESULT` 원인과 source turn/source key를 명시한다. 정수 overflow를 피하기 위해 delta 합산은 `long`으로 계산한 뒤 clamp한다.

## Retry / audit

NPC별로 최신 source turn의 source-key dedupe ledger만 bounded하게 보존한다. 같은 turn/key의 동일 command는 no-op retry가 되고, 같은 key를 다른 reason/delta로 재사용하면 거절한다. 같은 turn에 서로 다른 source key는 각각 정상 적용된다. 과거 source turn을 현재 상태에 다시 적용하는 것도 거절한다.

각 canonical 변화는 `GameResult.RelationshipChanged`로 기록하고 `game_log.relationship_changes_json`에 별도 저장한다. `GameTurnCommit`은 typed audit replay 결과와 next `RelationshipState`가 같은지 확인하며, snapshot이 없으면 `GameStateRecovery`가 ledger를 순서대로 replay한다.

## Snapshot

현재 snapshot schema는 v10이며 `relationshipState.relationships`가 필수다. v8 이하에는 typed relationship 의미가 없으므로 v8→v9 upgrade는 빈 관계 상태만 추가한다. v9→v10은 Story Memory ownership/summary 형식만 변경하며 relationship state는 그대로 보존한다. 과거 prose나 기존 StoryMemory에서 affinity/stage를 추정하지 않는다. 현재 v10 snapshot에 관계 필드가 누락되면 손상 데이터로 거절한다.

## Narrative memory projection

`NpcNarrativeMemory`는 public/private fact map을 구조적으로 분리한다. `NarrativeContext.NpcProjection`도 두 영역을 별도로 전달하여 provider가 정보 가시성 경계를 인지할 수 있게 한다. affinity/stage와 NPC identity는 `RelationshipState`에서 read-only projection하며 StoryMemory canonical fact로 이중 저장하지 않는다.

## Availability

`RelationshipStageCondition`은 canonical `RelationshipState`만 조회하므로 quest prerequisite나 action availability 규칙에서 display text나 narrative prose 없이 stage 조건을 재사용할 수 있다.
