# GameState snapshot evolution

## 목적

`game_state_snapshot.state_json`은 최신 canonical `GameState`를 빠르게 복구하기 위한 snapshot입니다. 도메인 구조가 확장되어도 기존 세션을 Jackson 기본값에 암묵적으로 의존해 읽지 않도록 JSON envelope에 명시적인 schema/ruleset version을 둡니다.

DB 컬럼을 추가하지 않고 `state_json` 내부 형식만 진화시키므로 기존 PostgreSQL/Flyway schema는 그대로 유지합니다.

## 현재 snapshot 형식

새 write는 schema `2`, ruleset `1`을 사용합니다.

```json
{
  "schemaVersion": 2,
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
    "storyMemory": {}
  }
}
```

- `schemaVersion`: snapshot JSON 구조/필드 의미의 evolution version
- `rulesetVersion`: 저장 상태를 해석하는 결정적 게임 규칙 계약 version
- `state`: canonical `GameState`

schema와 ruleset version은 서로 다른 축입니다. #7의 typed stats 추가는 저장 JSON 구조 변경이므로 schema를 2로 올렸지만, 과거 Skill Check 결과를 새로운 규칙으로 재판정하는 변경은 아니므로 ruleset은 1을 유지합니다.

## 지원 경로

### v0 raw GameState

#31 이전 production 형식은 envelope 없이 `GameState` 자체를 저장했습니다. logical schema v0, legacy ruleset baseline 1로 취급합니다.

v0은 먼저 v1 의미로 승격한 뒤 v2 변환을 적용합니다.

### v1 -> v2 typed stats

v1의 `playerCharacter.stats`는 `Map<String,Integer>` 형태였습니다. production 신규 캐릭터는 이 Map을 빈 값으로 생성했습니다.

v2 upgrade 규칙:

- stats가 없거나 canonical stat 값이 누락되면 서버 기본값 `10` 사용
- `MIGHT`, `AGILITY`, `INTELLECT`, `WILL`, `PRESENCE`와 현재 lowercase field 이름은 유효한 값이면 보존
- 허용 범위 `1~30` 밖의 값이나 비정수는 추정하지 않고 명시적으로 실패
- 알 수 없는 legacy key를 임의의 현재 stat으로 추정 매핑하지 않음
- provider, 랜덤, 시간, LLM 호출 없이 순수 JSON 변환만 수행

## read / write 정책

- **write:** 항상 현재 schema/ruleset version으로 저장
- **read:** 지원되는 과거 schema를 `GameStateUpgrader`에서 순수 변환한 뒤 현재 `GameState`로 역직렬화
- **미래 schema:** 명시적 실패
- **미지원 ruleset:** 자동 재판정하지 않고 명시적 실패
- **손상 snapshot:** legacy raw state로 명확히 식별되지 않으면 명시적 실패

읽기만으로 DB를 즉시 다시 쓰지 않습니다. read-time upgrade는 메모리에서만 수행하고, 다음 정상 canonical turn commit에서 최신 v2 envelope로 자연스럽게 재작성합니다.

## GameLog state version과의 관계

- `GameLog.state_version`: 세션 안의 canonical turn state 순서
- snapshot `schemaVersion`: JSON 저장 형식 evolution
- snapshot `rulesetVersion`: 결정적 규칙 계약 version

서로 비교하거나 대체하지 않습니다.

## snapshot 없는 session

snapshot이 없으면 append-only `GameLog`를 통해 `GameStateRecovery`가 현재 상태를 복구합니다. 복구되는 신규 `PlayerCharacter`에는 서버 기본 `CharacterStats`가 적용되고 이후 정상 write에서 schema v2 snapshot이 생성됩니다.

## 향후 규칙

새 schema version은 한 단계씩 순수 변환하는 upgrader와 version별 fixture/test를 추가합니다. 의미를 복구할 수 없는 값은 추정하지 말고 명시적 실패 또는 별도 migration 정책으로 처리합니다.
