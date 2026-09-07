# GameState snapshot evolution

## 목적

`game_state_snapshot.state_json`은 최신 canonical `GameState`를 빠르게 복구하기 위한 snapshot입니다. 도메인 구조가 확장되어도 기존 세션을 Jackson 기본값에 암묵적으로 의존해 읽지 않도록 JSON envelope에 명시적인 schema/ruleset version을 둡니다.

snapshot 구조는 `state_json` 내부에서 진화하고, 별도의 audit이 필요한 canonical 변화는 append-only `GameLog` migration으로 보강합니다.

## 현재 snapshot 형식

새 write는 schema `3`, ruleset `1`을 사용합니다.

```json
{
  "schemaVersion": 3,
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

schema와 ruleset version은 서로 다른 축입니다. typed stats와 inventory aggregate 추가는 저장 JSON 구조 변경이므로 schema를 각각 2, 3으로 올렸지만 과거 결과를 새로운 규칙으로 재판정하지 않으므로 ruleset은 1을 유지합니다.

## 지원 경로

### v0 raw GameState

#31 이전 production 형식은 envelope 없이 `GameState` 자체를 저장했습니다. logical schema v0, legacy ruleset baseline 1로 취급합니다.

v0은 v1, v2, v3 순서로 한 단계씩 승격합니다.

### v1 -> v2 typed stats

v1의 `playerCharacter.stats`는 `Map<String,Integer>` 형태였습니다. production 신규 캐릭터는 이 Map을 빈 값으로 생성했습니다.

v2 upgrade 규칙:

- stats가 없거나 canonical stat 값이 누락되면 서버 기본값 `10` 사용
- `MIGHT`, `AGILITY`, `INTELLECT`, `WILL`, `PRESENCE`와 현재 lowercase field 이름은 유효한 값이면 보존
- 허용 범위 `1~30` 밖의 값이나 비정수는 추정하지 않고 명시적으로 실패
- 알 수 없는 legacy key를 임의의 현재 stat으로 추정 매핑하지 않음
- provider, 랜덤, 시간, LLM 호출 없이 순수 JSON 변환만 수행

### v2 -> v3 inventory / equipment

v2에는 inventory 의미가 존재하지 않았으므로 안전하게 복구 가능한 값은 빈 inventory뿐입니다.

v3 upgrade 규칙:

- `inventory`가 없으면 `items: {}`와 `equipment.slots: {}`를 명시적으로 추가
- v2 문서에 이미 `inventory` 필드가 존재하지만 object가 아니면 손상 데이터로 실패
- 과거 story prose에서 item 획득·소비·장착을 추론하지 않음
- read-time upgrade는 DB write나 provider 호출 없이 순수 변환만 수행

현재 schema v3에서 inventory가 누락되거나 손상된 경우는 legacy로 간주하지 않고 역직렬화 실패로 처리합니다.

## read / write 정책

- **write:** 항상 현재 schema/ruleset version으로 저장
- **read:** 지원되는 과거 schema를 `GameStateUpgrader`에서 순수 변환한 뒤 현재 `GameState`로 역직렬화
- **미래 schema:** 명시적 실패
- **미지원 ruleset:** 자동 재판정하지 않고 명시적 실패
- **손상 snapshot:** legacy raw state로 명확히 식별되지 않으면 명시적 실패

읽기만으로 DB를 즉시 다시 쓰지 않습니다. read-time upgrade는 메모리에서만 수행하고, 다음 정상 canonical turn commit에서 최신 v3 envelope로 자연스럽게 재작성합니다.

## GameLog state version과의 관계

- `GameLog.state_version`: 세션 안의 canonical turn state 순서
- snapshot `schemaVersion`: JSON 저장 형식 evolution
- snapshot `rulesetVersion`: 결정적 규칙 계약 version

서로 비교하거나 대체하지 않습니다.

Inventory/equipment 변화는 snapshot에 최신 상태를 저장하는 것과 별도로 `game_log.inventory_changes_json`에 typed audit을 기록합니다. snapshot이 사라진 session은 이 audit을 turn 순서대로 replay해 동일 inventory/equipment를 복구합니다. legacy/opening log의 audit `NULL`은 변화 없음으로 해석합니다.

## snapshot 없는 session

snapshot이 없으면 append-only `GameLog`를 통해 `GameStateRecovery`가 현재 상태를 복구합니다. legacy log에는 inventory audit이 없으므로 빈 inventory에서 시작하고, 신규 audit이 있는 turn부터 typed state change를 replay합니다. 복구 뒤 다음 정상 write에서 schema v3 snapshot이 생성됩니다.

## 향후 규칙

새 schema version은 한 단계씩 순수 변환하는 upgrader와 version별 fixture/test를 추가합니다. 의미를 복구할 수 없는 값은 추정하지 말고 명시적 실패 또는 별도 migration 정책으로 처리합니다.
