# GameState snapshot evolution

## 목적

`game_state_snapshot.state_json`은 최신 canonical `GameState`를 빠르게 복구하기 위한 snapshot입니다. 도메인 구조가 확장되어도 기존 세션을 Jackson 기본값에 암묵적으로 의존해 읽지 않도록 JSON envelope에 명시적인 schema/ruleset version을 둡니다.

snapshot 구조는 `state_json` 내부에서 진화하고, 별도의 audit이 필요한 canonical 변화는 append-only `GameLog` migration으로 보강합니다.

## 현재 snapshot 형식

새 write는 schema `10`, ruleset `2`를 사용합니다. 현재 `GameState`에는 typed stats/vitals, inventory/equipment, combat encounter, ability cooldown, quest/objective와 typed World/Event Flag, NPC relationship/affinity 상태, 구조화 StoryMemory summary metadata가 포함됩니다.

- `schemaVersion`: snapshot JSON 구조/필드 의미의 evolution version
- `rulesetVersion`: 저장 상태를 해석하는 결정적 게임 규칙 계약 version (`GameRuleset.CURRENT_VERSION`)
- `SkillCheck.RULESET_VERSION`: 현재 canonical 규칙 버전을 참조하며 Skill Check audit/result의 판정 당시 규칙을 식별
- `state`: canonical `GameState`

schema와 ruleset version은 서로 다른 축입니다. 저장 구조는 v10까지 확장되었지만 과거 결과를 현재 규칙으로 재판정하지 않으므로 이번 Skill Check 신규 consequence 규칙부터 ruleset 2를 사용합니다. 기존 결과는 재판정하지 않습니다.

## schemaVersion과 rulesetVersion 변경 기준

- **schemaVersion만 올리는 경우:** JSON envelope/필드/타입 표현이 달라졌지만 기존 canonical 상태의 결정적 의미와 판정 규칙은 동일한 경우. 기존 schema 변환은 `GameStateUpgrader`에 명시적 단계로 구현합니다.
- **rulesetVersion을 올리는 경우:** DC·modifier·success/failure·HP/보상·상태 전이 등 결정적 규칙 계약이 달라져 같은 입력을 새 규칙으로 판정했을 때 결과가 달라질 수 있는 경우. 공통 버전은 `GameRuleset.CURRENT_VERSION`이며 Skill Check의 현재 판정 버전과 snapshot 버전이 같은 계약을 따릅니다.
- schema와 ruleset을 동시에 바꿀 수 있으나 각각의 호환성 근거와 테스트를 따로 제시해야 합니다. `GameLog.state_version`(턴 순서)이나 typed audit 자체의 `schemaVersion`과 혼동하지 않습니다.
- **판정 시점의 결과는 불변입니다.** 과거 Skill Check roll/DC/modifier/outcome 및 committed HP/상태 변화를 현재 규칙으로 재계산하지 않습니다. 저장된 과거 Skill Check 결과 검증은 해당 결과의 ruleset 의미를 보존해야 하며, 새 규칙으로 재채점해서는 안 됩니다.

## ruleset transition 정책

`GameStateRulesetTransitions`는 schema upgrader와 분리된 명시적 규칙 호환성 경계입니다. 읽을 때 먼저 원본 ruleset에서 현재 버전까지 등록된 **연속된 한 단계 전환**이 있는지 확인하고, 없다면 schema 변환 이전에 거부합니다. 등록된 전환만 schema upgrade 후 메모리에서 수행하며, 마지막에 현재 shape를 검증합니다. 전환 구현은 원본 JSON을 변경하지 않아야 하고, provider 호출·랜덤 판정·과거 turn 재판정 없이 동일 입력에 동일 결과를 만들어야 합니다.

**현재 production ruleset은 2이며 1→2 상태 보존 identity transition만 등록됩니다.** 이 전환은 신규 Skill Check의 미래 consequence만 변경하며 과거 snapshot과 결과는 그대로 유지합니다. 따라서 과거 schema v0~v9의 ruleset 1 데이터도 읽을 수 있으며 ruleset 0/음수, 누락, 미래/미지원 버전은 명시적으로 거부합니다. 테스트에서는 production 규칙 버전을 올리지 않고 별도 target/가상 step으로 transition chain, 누락 단계, 결정성, 원본 불변성을 검증합니다. 임의의 version 숫자 치환이나 migration에 의한 판정 변경은 지원하지 않습니다.

상태 의미 자체가 달라진 **incompatible** ruleset이라면 read-time transition을 등록하지 않고 snapshot을 거부해야 합니다. 필요 시 별도의 버전별 런타임 또는 근거 있는 오프라인 migration을 설계해야 하며, 과거 canonical 결과를 묵시적으로 현재 규칙으로 재판정해서는 안 됩니다.

## 지원 경로

### v0 raw GameState

#31 이전 production 형식은 envelope 없이 `GameState` 자체를 저장했습니다. logical schema v0, legacy ruleset baseline 1로 취급하며 v0부터 v10까지 한 단계씩 순수 변환합니다.

### v1 -> v2 typed stats

v1의 `playerCharacter.stats`는 `Map<String,Integer>` 형태였습니다.

- 누락 canonical stat은 서버 기본값 10 사용
- 유효한 기존 canonical 값은 보존
- 허용 범위 밖 값/비정수는 실패
- 알 수 없는 legacy key를 추정 매핑하지 않음

### v2 -> v3 inventory / equipment

v2에는 inventory 의미가 존재하지 않았으므로 빈 inventory만 추가합니다. schema v2에 이미 `inventory`가 있으면 정의되지 않은 의미를 추정하지 않고 실패하며 과거 prose에서 item 상태를 추론하지 않습니다.

### v3 -> v4 HP / MP / Status Effect

v3에는 vitals 의미가 존재하지 않았으므로 HP 10/10, MP 10/10, 빈 status를 명시적으로 추가합니다. 이미 정의되지 않은 `vitals`가 있거나 prose에 부상/마나/status 표현이 있어도 이를 canonical 상태로 추정하지 않습니다.

### v4 -> v5 Combat Encounter / EnemyState

v4에는 combat encounter의 canonical 의미가 없으므로 `combatEncounter: null`만 추가합니다. 기존 세션을 전투 중이었다고 추정하거나 과거 story prose에서 enemy/참가자/lifecycle/current actor를 복구하지 않습니다.

### v5 -> v6 Combat Modifier / Enemy Combat Profile

v5의 item/enemy에는 #42의 전투 modifier/profile 의미가 없으므로 기존 item의 attack/damage bonus는 0, enemy profile은 defense 10 / damage reduction 0으로 deterministic하게 승격합니다. 이미 정의되지 않은 신규 필드가 있으면 추측하지 않고 실패합니다.

### v6 -> v7 Ability State

v6에는 ability cooldown 의미가 없으므로 `abilityState.cooldowns`에 빈 map만 추가합니다. 과거 MP 사용이나 story 표현에서 cooldown을 추론하지 않습니다.

### v7 -> v8 Quest / Objective / Flag State

v7에는 typed quest/flag 의미가 없으므로 `questState.quests`, `worldFlags`, `eventFlags`를 모두 빈 map으로 추가합니다. 기존 `WorldState.flags`는 과거 의미를 추측해 새 `WorldFlag`/`EventFlag`로 승격하지 않습니다.

### v8 -> v9 NPC Relationship / Affinity State

v8에는 typed NPC relationship 의미가 없으므로 `relationshipState.relationships`를 빈 map으로 추가합니다. 과거 story prose나 StoryMemory에서 NPC identity, affinity, stage를 추론하지 않습니다.

### v9 -> v10 Story Memory ownership / summary metadata

v9 `StoryMemory.canonicalFacts`는 `sourceTurn/status`가 없는 과거 형식입니다. v10은 GameState가 소유하는 canonical 사실과 narrative memory 책임을 분리합니다.

- v9 fact가 `world.premise`, `player.description` 또는 inventory/vitals/quest/relationship/NPC/world flag/event/combat/ability 등 state-owned key이면 GameState projection에 이미 존재하므로 StoryMemory에서 제거합니다.
- 비 state-owned fact는 `sourceTurn/status`를 안전하게 복구할 근거가 없으므로 임의 기본값을 넣지 않고 명시적으로 실패합니다.
- 과거 `rollingSummary` 문자열은 source turn range/state version을 안전하게 복구할 수 없으므로 빈 구조화 summary(`sourceFromTurn=0`, `sourceToTurn=0`, `stateVersion=0`, `text=""`)로 전환합니다. 과거 prose를 현재 canonical 사실로 재해석하지 않습니다.
- `recentTurns`는 그대로 보존합니다.

## 현재 schema 검증

현재 v10 snapshot은 stats, inventory, vitals, combatEncounter, abilityState, questState, relationshipState와 StoryMemory 구조를 명시적으로 검증합니다. `rollingSummary`에는 `sourceFromTurn`, `sourceToTurn`, `stateVersion`, `text`가 모두 있어야 합니다. 현재 schema의 필드 누락이나 손상 값을 legacy로 간주해 조용히 기본값으로 복구하지 않습니다.

특히 ability cooldown의 잘못된 값, quest/objective shape 불일치, COUNT progress의 target 초과, flag namespace/key/value/version 불변식 위반, relationship affinity/stage/identity 불변식 위반, StoryMemory summary metadata 누락은 현재 상태 손상으로 거절합니다.

## read / write 정책

- **write:** 항상 현재 schema/ruleset version으로 저장
- **read:** 지원되는 과거 schema를 `GameStateUpgrader`에서 한 단계씩 순수 변환한 뒤 현재 `GameState`로 역직렬화
- **미래 schema:** 명시적 실패
- **미지원 ruleset:** 먼저 전환 경로 존재 여부를 확인하고, 전환 미등록/누락 단계는 자동 재판정하지 않고 명시적 실패
- **손상 snapshot:** legacy raw state로 명확히 식별되지 않으면 명시적 실패

읽기만으로 DB를 즉시 다시 쓰지 않습니다. read-time upgrade는 메모리에서만 수행하고 다음 정상 canonical turn commit에서 최신 v10 envelope로 자연스럽게 재작성합니다.

## GameLog state version과의 관계

- `GameLog.state_version`: 세션 안의 canonical turn state 순서
- snapshot `schemaVersion`: JSON 저장 형식 evolution
- snapshot `rulesetVersion`: 결정적 규칙 계약 version

서로 비교하거나 대체하지 않습니다.

현재 typed audit은 inventory/equipment를 `inventory_changes_json`, HP/MP/status를 `vitals_changes_json`, combat/attack/ability/cooldown을 `combat_changes_json`, quest/objective/World/Event Flag를 `quest_changes_json`, NPC relationship/affinity를 `relationship_changes_json`에 기록합니다. V21 이후 non-opening committed turn은 snapshot 손실 시 compacted narrative memory 의미까지 보존할 수 있도록 commit 후 `StoryMemory`를 `story_memory_json`에도 기록합니다. 이 audit은 `schemaVersion=1` envelope을 사용하며 지원하지 않는 미래 version은 묵시적으로 읽지 않습니다. legacy typed audit `NULL`은 해당 변화 없음으로 해석하고, legacy `story_memory_json = NULL`은 summary를 추정하지 않은 기존 transcript replay 경계로 해석합니다.

## snapshot 없는 session

snapshot이 없으면 append-only `GameLog`를 통해 `GameStateRecovery`가 현재 상태를 복구합니다. legacy log에는 신규 audit이 없으므로 각 aggregate의 안전한 baseline에서 시작하고 audit이 있는 turn부터 typed state change를 순서대로 replay합니다. `story_memory_json`이 있는 turn은 그 값의 필수 shape와 source/range/stateVersion 불변식을 검증한 뒤 실제 commit된 StoryMemory projection을 적용합니다. `NULL`인 legacy turn은 action/story transcript만 replay하며 rolling summary나 canonical fact를 현재 규칙으로 재생성하지 않습니다. 값이 존재하지만 손상된 StoryMemory audit은 legacy로 취급하지 않고 명시적으로 실패합니다. 복구 뒤 다음 정상 write에서 schema v10 snapshot이 생성됩니다.

## 향후 ruleset 변경 PR checklist

- [ ] 변경이 저장 구조 변경인지 결정적 규칙 변경인지 구분하고, `GameRuleset.CURRENT_VERSION`/schema 버전 상승 여부를 결정한다.
- [ ] 변경 이전 snapshot, 기존 GameLog/Skill Check decision/audit의 의미를 조사하고, old/new 상태 해석과 판정이 같은지 증거를 작성한다.
- [ ] **state-preserving**이라고 입증한 이전 ruleset에만 구체적인 `StatePreservingTransition` hook과 모든 연속 단계 경로를 등록한다. 그렇지 않으면 미지원으로 유지한다.
- [ ] schema upgrade와 ruleset transition을 따로 검증하고, 누락/미래/손상 입력은 조용한 기본값 없이 명시적으로 실패하도록 테스트한다.
- [ ] migration에 provider·랜덤·과거 Skill Check 재판정을 포함하지 않고, 재실행 결정성/원본 불변성/기존 turn 및 audit 보존을 검증한다.
- [ ] historical Skill Check 결과 deserialization/검증이 판정 당시 ruleset에 맞는지 확인한다. 새 판정 공식을 과거 outcome에 대입하지 않는다.
- [ ] snapshot 없는 `GameLog` replay도 과거 canonical mutation을 그대로 적용할 수 있는지 점검한다. 현재 복구는 별도 historical ruleset metadata를 보유하지 않으므로 incompatible 변경을 지원한다고 주장할 수 없다.
- [ ] read-time 변환은 side effect 없이 수행하고, 이후 정상 commit 때만 새로운 envelope을 write하는지 확인한다.
- [ ] version별 production/legacy fixture, corrupt/current/future snapshot, PostgreSQL recovery/audit 및 retry 회귀 테스트를 추가하고 README·architecture 계약을 갱신한다.

새 schema version은 한 단계씩 순수 변환하는 upgrader와 version별 fixture/test를 추가합니다. 의미를 안전하게 복구할 수 없는 값은 추정하지 말고 명시적 실패 또는 별도 migration 정책으로 처리합니다.
