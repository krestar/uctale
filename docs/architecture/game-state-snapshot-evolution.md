# GameState snapshot evolution

## 목적

`game_state_snapshot.state_json`은 최신 canonical `GameState`를 빠르게 복구하기 위한 snapshot입니다. 도메인 구조가 확장되어도 기존 세션을 Jackson 기본값에 암묵적으로 의존해 읽지 않도록 JSON envelope에 명시적인 schema/ruleset version을 둡니다.

snapshot 구조는 `state_json` 내부에서 진화하고, 별도의 audit이 필요한 canonical 변화는 append-only `GameLog` migration으로 보강합니다.

## 현재 snapshot 형식

새 write는 schema `4`, ruleset `1`을 사용합니다.

```json
{
  "schemaVersion": 4,
  "rulesetVersion": 1,
  "state": {
    "turnNumber": 1,
    "playerCharacter": {
      "description": "캐릭터",
      "stats": {
        "might": 10,
        "agility": 10,
        "intellect": 10,
        "will": 10,
        "presence": 10
      },
      "vitals": {
        "hp": {"current": 10, "max": 10},
        "mp": {"current": 10, "max": 10},
        "statusEffects": {}
      }
    },
    "worldState": {},
    "storyMemory": {},
    "inventory": {
      "items": {},
      "equipment": {"slots": {}}
    }
  }
}
```

- `schemaVersion`: snapshot JSON 구조/필드 의미의 evolution version
- `rulesetVersion`: 저장 상태를 해석하는 결정적 게임 규칙 계약 version
- `state`: canonical `GameState`

schema와 ruleset version은 서로 다른 축입니다. typed stats, inventory aggregate, vitals/status aggregate 추가는 저장 JSON 구조 변경이므로 schema를 각각 2, 3, 4로 올렸지만 과거 결과를 새로운 규칙으로 재판정하지 않으므로 ruleset은 1을 유지합니다.

## 지원 경로

### v0 raw GameState

#31 이전 production 형식은 envelope 없이 `GameState` 자체를 저장했습니다. logical schema v0, legacy ruleset baseline 1로 취급합니다. v0은 v1, v2, v3, v4 순서로 한 단계씩 승격합니다.

### v1 -> v2 typed stats

v1의 `playerCharacter.stats`는 `Map<String,Integer>` 형태였습니다.

- 누락 canonical stat은 서버 기본값 10 사용
- 유효한 기존 canonical 값은 보존
- 허용 범위 1~30 밖 값/비정수는 실패
- 알 수 없는 legacy key를 추정 매핑하지 않음

### v2 -> v3 inventory / equipment

v2에는 inventory 의미가 존재하지 않았으므로 빈 inventory만 안전하게 복구합니다.

- `items: {}`, `equipment.slots: {}`를 명시적으로 추가
- schema v2에 `inventory`가 이미 있으면 정의되지 않은 의미를 추정하지 않고 실패
- 과거 prose에서 item 상태를 추론하지 않음

### v3 -> v4 HP / MP / Status Effect

v3에는 vitals 의미가 존재하지 않았으므로 안전한 baseline만 명시적으로 추가합니다.

- HP 10/10
- MP 10/10
- 빈 `statusEffects`
- schema v3에 `playerCharacter.vitals`가 이미 있으면 정의되지 않은 의미를 추정하지 않고 실패
- 과거 prose에서 부상, 마나, 상태 효과를 추론하지 않음

현재 schema v4에서 stats, inventory, vitals 등 필수 구조가 누락되거나 손상되면 legacy로 간주하지 않고 역직렬화 실패로 처리합니다.

## read / write 정책

- **write:** 항상 현재 schema/ruleset version으로 저장
- **read:** 지원되는 과거 schema를 `GameStateUpgrader`에서 순수 변환한 뒤 현재 `GameState`로 역직렬화
- **미래 schema:** 명시적 실패
- **미지원 ruleset:** 자동 재판정하지 않고 명시적 실패
- **손상 snapshot:** legacy raw state로 명확히 식별되지 않으면 명시적 실패

읽기만으로 DB를 즉시 다시 쓰지 않습니다. read-time upgrade는 메모리에서만 수행하고, 다음 정상 canonical turn commit에서 최신 v4 envelope로 자연스럽게 재작성합니다.

## GameLog state version과의 관계

- `GameLog.state_version`: 세션 안의 canonical turn state 순서
- snapshot `schemaVersion`: JSON 저장 형식 evolution
- snapshot `rulesetVersion`: 결정적 규칙 계약 version

서로 비교하거나 대체하지 않습니다.

Inventory/equipment 변화는 `game_log.inventory_changes_json`, HP/MP/status 변화는 `game_log.vitals_changes_json`에 typed audit을 별도로 기록합니다. snapshot이 사라진 session은 이 audit들을 turn 순서대로 replay해 canonical 상태를 복구합니다. legacy/opening log의 audit `NULL`은 변화 없음입니다.

## snapshot 없는 session

snapshot이 없으면 append-only `GameLog`를 통해 `GameStateRecovery`가 현재 상태를 복구합니다. legacy log에는 신규 audit이 없으므로 기본 inventory/vitals에서 시작하고, audit이 있는 turn부터 typed state change를 replay합니다. 복구 뒤 다음 정상 write에서 schema v4 snapshot이 생성됩니다.

## 향후 규칙

새 schema version은 한 단계씩 순수 변환하는 upgrader와 version별 fixture/test를 추가합니다. 의미를 복구할 수 없는 값은 추정하지 말고 명시적 실패 또는 별도 migration 정책으로 처리합니다.
