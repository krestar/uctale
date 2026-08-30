# GameState snapshot evolution

## 목적

`game_state_snapshot.state_json`은 최신 canonical `GameState`를 빠르게 복구하기 위한 캐시성 snapshot이다. 도메인 필드가 확장되어도 기존 세션을 암묵적 Jackson 기본값에만 의존해 읽지 않도록 JSON 자체에 명시적인 version contract를 둔다.

이 변경은 DB 컬럼을 추가하지 않는다. 버전은 `state_json` envelope 안에 기록하므로 기존 PostgreSQL schema와 Flyway chain을 그대로 사용한다.

## 현재 snapshot 형식

새 write는 항상 현재 형식만 기록한다.

```json
{
  "schemaVersion": 1,
  "rulesetVersion": 1,
  "state": {
    "turnNumber": 1,
    "playerCharacter": {},
    "worldState": {},
    "storyMemory": {}
  }
}
```

- `schemaVersion`: snapshot JSON의 구조와 필드 의미가 어떤 evolution 단계인지 나타낸다.
- `rulesetVersion`: 해당 snapshot을 해석할 때 전제로 삼는 결정적 게임 규칙 계약을 나타낸다. 오래된 ruleset을 현재 규칙으로 자동 재판정하는 용도가 아니다.
- `state`: 해당 시점의 canonical `GameState` payload다.

현재 지원 버전은 schema `1`, ruleset `1`이다.

## legacy production snapshot

#31 이전 production 형식은 envelope 없이 `GameState` 자체를 `state_json`에 저장했다. 이 형식은 논리적인 schema version `0`으로 취급한다.

legacy 판별은 단순히 `schemaVersion` 누락 여부만 보지 않는다. 기존 raw `GameState`의 최상위 필드인 `turnNumber`, `playerCharacter`, `worldState`, `storyMemory`가 모두 존재하고 envelope 필드가 없어야 한다. 따라서 `schemaVersion`만 유실된 손상 envelope를 legacy로 오인하지 않는다.

v0 -> v1 upgrade는 JSON 구조 변환만 수행한다. provider 호출, 랜덤 판정, LLM 호출, 현재 ruleset에 따른 재판정은 수행하지 않는다.

## read / write 정책

- **write:** 항상 현재 schema/ruleset version의 envelope만 저장한다.
- **read:** 지원되는 과거 schema를 `GameStateUpgrader`에서 순수 변환한 뒤 현재 `GameState`로 역직렬화한다.
- **미래 schema:** 현재 서버가 의미를 알 수 없으므로 명시적으로 실패한다.
- **미지원 ruleset:** 임의 재판정을 피하기 위해 명시적으로 실패한다.
- **누락/손상 version 또는 state:** legacy raw state로 명확히 식별되지 않는 한 명시적으로 실패한다.

### upgrade 후 DB 재작성 정책

읽기만으로 snapshot을 즉시 재작성하지 않는다.

이유는 다음과 같다.

1. `loadLatestTurn()`의 read-only 경계를 유지한다.
2. 조회 트래픽만으로 DB write가 발생하거나 장애 시점에 대량 write가 생기는 것을 피한다.
3. legacy 데이터를 읽을 수 있는 동안 rollback 가능성을 불필요하게 줄이지 않는다.

대신 다음 canonical turn이 정상 commit될 때 기존 snapshot은 현재 버전 envelope로 자연스럽게 재작성된다. 따라서 **read-time upgrade는 in-memory only, next canonical write는 latest-only** 정책을 사용한다.

## GameLog state version과의 관계

#28에서 도입된 `GameLog.previous_state_version` / `state_version`과 snapshot version은 서로 다른 축이다.

- `GameLog.state_version`: 한 세션 안에서 어떤 canonical turn state인지 식별하는 순서 값이다. 현재 opening은 `0 -> 1`, 이후 완료 turn은 `N-1 -> N`으로 진행한다.
- snapshot `schemaVersion`: JSON 저장 형식의 evolution version이다.
- snapshot `rulesetVersion`: 결정적 게임 규칙 계약의 version이다.

세 값은 숫자가 우연히 같더라도 서로 비교하거나 대체해서는 안 된다. 예를 들어 turn 20의 state는 `state_version=20`이면서 snapshot은 `schemaVersion=1`, `rulesetVersion=1`일 수 있다.

## snapshot 없는 session 복구와 migration 순서

현재 `main`에는 #28의 append-only committed-turn ledger가 Flyway V6로 이미 반영되어 있다.

정상 배포 순서는 다음과 같다.

1. production Flyway migration을 V6 이상까지 적용한다.
2. #31 애플리케이션을 배포한다.
3. snapshot이 존재하면 v0/v1 snapshot read 경로를 사용한다.
4. snapshot이 없으면 V6 committed `GameLog`의 `input_choice_text`, `previous_state_version`, `state_version`을 검증하며 `GameStateRecovery`가 state를 재구성한다.
5. 이후 정상 turn commit 시 현재 snapshot envelope가 다시 생성된다.

V1~V5 시절 snapshot raw JSON은 DB migration으로 일괄 변환하지 않는다. V6가 legacy `GameLog.user_choice`를 다음 committed row의 `input_choice_text`로 이관하기 때문에 snapshot 없는 기존 session도 #28 이후 원장 구조에서 결정적으로 복구할 수 있다.

## 향후 schema 추가 규칙

새 schema version을 도입할 때는 한 단계씩 순수 변환하는 upgrader를 추가하고 version별 fixture를 유지한다. upgrade 중 외부 provider, 시간 의존 값, 랜덤 값, LLM 결과를 사용해서는 안 된다. 데이터 의미를 복구할 수 없는 변경은 추정값을 만들어 넣지 말고 명시적 실패 또는 별도 migration 정책으로 처리한다.
