# Skill Ability / Resource / Cooldown 규칙

## 목적

Ability의 사용 가능 여부, MP 비용, 효과, target, cooldown은 Narrative provider가 아니라 서버가 한 canonical transition에서 확정한다. LLM은 이미 확정된 결과를 서술할 뿐 비용·피해·회복·status·cooldown을 새로 만들거나 바꿀 수 없다.

## 최소 모델

`AbilityDefinition`은 `definitionId`, `mpCost`, `cooldownTurns`, `targetRule`, effect 정의를 가진다. 현재 fixture catalog는 경계 검증을 위해 세 종류만 둔다.

- `arcane-bolt`: enemy 대상 고정 damage
- `second-wind`: self heal
- `steady-focus`: self status effect

대형 skill tree, 레벨별 자동 학습, player-authored script, 최종 balance는 #43 범위가 아니다.

## 서버 전이 순서

`COMBAT_ABILITY`는 서버가 발급한 `UseAbilityAction(encounterId, abilityDefinitionId, targetId)`으로만 실행한다. `AbilityRules`는 다음 순서를 유지한다.

1. active encounter / player turn / 행동 가능 상태 검증
2. definition과 target 검증
3. 현재 cooldown과 MP 검증
4. 이전 턴에서 남아 있던 cooldown의 turn-end 감소
5. MP 비용 소비
6. damage/heal/status 효과 적용
7. combat lifecycle/current actor 조정
8. 사용한 ability의 새 cooldown 설정
9. `AbilityResolved`, `AbilityCooldownChanged`와 관련 vitals/combat state change 기록
10. turn advance

새로 설정한 cooldown은 같은 사용 턴에서 감소하지 않는다. 예를 들어 cooldown 2 ability를 사용한 직후 값은 2이고, 이후 성공적으로 완료된 다른 턴마다 1씩 감소한다.

## 불변식

- MP가 부족하면 비용이나 효과를 일부 적용하지 않고 거절한다.
- cooldown이 남아 있으면 거절한다.
- `SELF` target은 player만, `ENEMY` target은 현재 encounter의 non-defeated enemy만 허용한다.
- 최대 HP에서 heal, 이미 같은 definition의 status가 있는 상태에서 현재 최소 status fixture 재적용은 거절한다.
- ability turn에는 외부 `InventoryCommand`/`VitalsCommand`를 함께 주입하지 않는다. ability 자체가 비용과 효과의 원자 경계를 소유한다.
- damage/heal/status 결과와 MP/cooldown은 모두 provider 호출 전에 결정된다.

## Persistence / recovery

`abilityState.cooldowns`는 schema v7에서 도입되었고 현재 snapshot schema v8에도 필수 canonical 상태로 저장된다. 기존 v6에는 ability 상태 의미가 없으므로 v6→v7 upgrade는 deterministic empty cooldown map만 추가하며, v7→v8은 ability 상태를 그대로 보존하고 빈 quest/flag 상태만 추가한다.

현재 v8 snapshot에서 `abilityState`나 `cooldowns`가 누락되거나 cooldown 값이 0/비정수인 경우 손상 데이터로 실패하며 조용히 기본값으로 복구하지 않는다.

Ability audit은 기존 `game_log.combat_changes_json`에 `ABILITY_RESOLVED`와 `ABILITY_COOLDOWN_CHANGED` typed entry로 저장한다. 별도 DB 컬럼을 추가하지 않는다. snapshot이 없어도 `GameStateRecovery`가 vitals/combat audit과 함께 cooldown audit을 replay해 동일 상태를 복구한다.

`GameTurnCommit`은 previous ability state에 cooldown audit을 replay한 결과가 next state와 정확히 같은지 저장 직전에 검증한다. 이미 commit된 turn의 중복 저장은 기존 optimistic/turn conflict 경계에서 거절되므로 비용·효과·cooldown이 두 번 적용되지 않는다.

## NarrativeContext

`NarrativeContext`에는 canonical next state의 `abilityCooldowns`, typed ability state changes, 서버가 만든 narrative cue를 전달한다. guardrail은 ability 비용·효과·target·cooldown과 MP/HP/status를 provider가 다시 판정하거나 변경하지 못하도록 명시한다.
