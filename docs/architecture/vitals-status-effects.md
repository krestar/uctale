# HP / MP / Status Effect canonical state

## 목적

HP, MP, 상태 효과, 행동불능/패배 여부는 narrative prose가 아니라 서버가 결정하는 canonical game state입니다. #40에서 도입한 타입 안전한 상태와 전이/audit/recovery 경계는 현재 combat/ability 규칙의 player/enemy resource 기반으로 사용됩니다.

## 도메인 모델

`PlayerCharacter.vitals`는 다음을 소유합니다.

- `hp`: `ResourcePool(current, max)`
- `mp`: `ResourcePool(current, max)`
- `statusEffects`: stable status definition ID -> `StatusEffect`

`ResourcePool`은 `max >= 1`, `0 <= current <= max`를 항상 보장합니다. 현재 신규/legacy 캐릭터의 baseline은 HP 10/10, MP 10/10입니다. 이 값은 호환 가능한 초기값이며 최종 전투 밸런스를 의미하지 않습니다.

`StatusEffect`의 최소 계약은 definition ID, stacks, intensity, remaining turns, expiry trigger, incapacitating 여부입니다. 현재 expiry trigger는 `END_OF_TURN`만 지원합니다. 동일 definition ID를 암묵적으로 합치지 않으며 stack/intensity/duration 변경은 명시적 update로만 수행합니다.

## 파생 상태

- `defeated`: HP가 0이면 true
- `incapacitated`: defeated이거나 하나 이상의 `incapacitating` status가 있으면 true

이 파생 상태는 combat/ability action의 유효성 및 encounter lifecycle 검증에도 사용됩니다. 영구 사망/부활 상세 규칙은 아직 별도 범위입니다.

## 서버 명령과 timing

`VitalsCommand` / `VitalsRules`는 provider와 무관한 순수 도메인 규칙입니다.

지원 명령은 damage/heal, spend/restore mana, status apply/update/remove, duration advance입니다. HP damage/heal과 MP restore는 실제 변화량을 0..max 경계에 맞춰 clamp하고 MP spend는 현재 MP를 초과하면 실패합니다. 산술은 `long` 중간값을 사용해 int overflow로 경계를 우회하지 않습니다.

Status duration timing은 `ActionResolver`가 소유합니다. 각 정상 turn resolution의 끝에서 `END_OF_TURN`을 정확히 한 번 진행하며, 같은 turn에 새로 적용되거나 명시적으로 갱신된 status는 즉시 duration을 차감하지 않습니다. 외부 호출자가 timing 명령을 주입해 두 번 감소시키는 것은 거절합니다.

## GameResult / Narrative 경계

서버가 적용한 변화는 `GameResult.StateChange`의 vitals/status typed change로 남깁니다. `NarrativeContext`에는 canonical next state의 vitals와 defeated/incapacitated 파생값, 서버 확정 state change가 provider 호출 전에 전달됩니다. LLM은 이를 서술할 수 있지만 HP/MP/status/생사 의미를 새로 결정할 수 없습니다.

Combat attack과 ability가 만드는 damage/heal/MP/status 변화도 동일한 canonical vitals 불변식을 사용합니다. 세부 전투 규칙은 [combat-encounter.md](./combat-encounter.md), ability 규칙은 [ability-cooldown.md](./ability-cooldown.md)를 기준으로 합니다.

## persistence / retry / recovery

`GameTurnCommit`은 전달된 state changes를 previous vitals에 replay한 결과가 next state의 vitals와 정확히 일치해야 commit을 허용합니다. `game_log.vitals_changes_json`에는 vitals/status 관련 typed audit을 저장합니다.

기존 transaction/idempotency/reservation/optimistic-lock 경계를 사용하므로 이미 commit된 stale/retry 요청이 같은 damage 또는 MP spend를 다시 canonical state에 적용할 수 없습니다. Snapshot이 없으면 `GameStateRecovery`가 baseline vitals에서 시작해 turn 순서대로 audit을 replay합니다. legacy `NULL` audit은 변화 없음이며 story prose에서 과거 HP/MP/status를 추정하지 않습니다.

## snapshot / legacy

`playerCharacter.vitals`는 schema v4에서 도입되었고 현재 schema v8에도 필수입니다.

- v0/v1/v2/v3 snapshot은 deterministic read-time upgrade에서 HP 10/10, MP 10/10, 빈 status를 명시적으로 추가합니다.
- schema v3에 정의되지 않은 `vitals` 필드가 이미 존재하면 의미를 추측해 승격하지 않고 실패합니다.
- 이후 v5~v8 upgrade는 이미 확정된 vitals를 재판정하지 않고 보존합니다.
- 현재 v8에서 vitals 누락/손상은 기본값으로 숨기지 않고 실패합니다.
- upgrade는 DB write/provider/random/time 의존 없이 순수 JSON 변환입니다.

## 후속 범위

영구 사망/부활 상세 규칙, 대규모 status catalog, 자동 regen 밸런스, frontend HUD 등은 별도 범위입니다. 기본 전투 turn order와 공격/방어, ability의 MP/status 효과는 이미 현재 main에 구현되어 있습니다.
