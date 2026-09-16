# GameState snapshot evolution

## 목적

`game_state_snapshot.state_json`은 최신 canonical `GameState`를 빠르게 복구하기 위한 snapshot입니다. 도메인 구조가 확장되어도 기존 세션을 Jackson 기본값에 암묵적으로 의존해 읽지 않도록 JSON envelope에 명시적인 schema/ruleset version을 둡니다.

snapshot 구조는 `state_json` 내부에서 진화하고, 별도의 audit이 필요한 canonical 변화는 append-only `GameLog` migration으로 보강합니다.

## 현재 snapshot 형식

새 write는 schema `9`, ruleset `1`을 사용합니다. 현재 `GameState`에는 typed stats/vitals, inventory/equipment, combat encounter, ability cooldown, quest/objective와 typed World/Event Flag, NPC relationship/affinity 상태가 포함됩니다.

- `schemaVersion`: snapshot JSON 구조/필드 의미의 evolution version
- `rulesetVersion`: 저장 상태를 해석하는 결정적 게임 규칙 계약 version
- `state`: canonical `GameState`

schema와 ruleset version은 서로 다른 축입니다. 저장 구조는 v9까지 확장되었지만 과거 결과를 현재 규칙으로 재판정하지 않으므로 ruleset은 1을 유지합니다.

## 지원 경로

### v0 raw GameState

#31 이전 production 형식은 envelope 없이 `GameState` 자체를 저장했습니다. logical schema v0, legacy ruleset baseline 1로 취급하며 v0부터 v9까지 한 단계씩 순수 변환합니다.

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

## 현재 schema 검증

현재 v9 snapshot은 stats, inventory, vitals, combatEncounter, abilityState, questState, relationshipState 등 현재 schema의 필수 구조를 명시적으로 검증합니다. 현재 schema의 필드 누락이나 손상 값을 legacy로 간주해 조용히 기본값으로 복구하지 않습니다.

특히 ability cooldown의 잘못된 값, quest/objective shape 불일치, COUNT progress의 target 초과, flag namespace/key/value/version 불변식 위반, relationship affinity/stage/identity 불변식 위반은 현재 상태 손상으로 거절합니다.

## read / write 정책

- **write:** 항상 현재 schema/ruleset version으로 저장
- **read:** 지원되는 과거 schema를 `GameStateUpgrader`에서 한 단계씩 순수 변환한 뒤 현재 `GameState`로 역직렬화
- **미래 schema:** 명시적 실패
- **미지원 ruleset:** 자동 재판정하지 않고 명시적 실패
- **손상 snapshot:** legacy raw state로 명확히 식별되지 않으면 명시적 실패

읽기만으로 DB를 즉시 다시 쓰지 않습니다. read-time upgrade는 메모리에서만 수행하고 다음 정상 canonical turn commit에서 최신 v9 envelope로 자연스럽게 재작성합니다.

## GameLog state version과의 관계

- `GameLog.state_version`: 세션 안의 canonical turn state 순서
- snapshot `schemaVersion`: JSON 저장 형식 evolution
- snapshot `rulesetVersion`: 결정적 규칙 계약 version

서로 비교하거나 대체하지 않습니다.

현재 typed audit은 inventory/equipment를 `inventory_changes_json`, HP/MP/status를 `vitals_changes_json`, combat/attack/ability/cooldown을 `combat_changes_json`, quest/objective/World/Event Flag를 `quest_changes_json`, NPC relationship/affinity를 `relationship_changes_json`에 기록합니다. legacy/opening log의 audit `NULL`은 해당 변화 없음으로 해석합니다.

## snapshot 없는 session

snapshot이 없으면 append-only `GameLog`를 통해 `GameStateRecovery`가 현재 상태를 복구합니다. legacy log에는 신규 audit이 없으므로 각 aggregate의 안전한 baseline에서 시작하고 audit이 있는 turn부터 typed state change를 순서대로 replay합니다. 복구 뒤 다음 정상 write에서 schema v9 snapshot이 생성됩니다.

## 향후 규칙

새 schema version은 한 단계씩 순수 변환하는 upgrader와 version별 fixture/test를 추가합니다. 의미를 안전하게 복구할 수 없는 값은 추정하지 말고 명시적 실패 또는 별도 migration 정책으로 처리합니다.
