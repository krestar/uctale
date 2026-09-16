# UCTale V2 GameState / Story Memory

## 목표

UCTale의 결정적 게임 상태는 서버가 소유하고, LLM은 그 상태와 서버가 확정한 결과를 서사로 표현하는 Narrative Engine으로 동작합니다.

## canonical GameState

`GameState`는 한 세션의 복원 가능한 현재 상태입니다.

- `turnNumber`: 현재 적용된 턴 번호
- `PlayerCharacter`: 설명, `CharacterStats`, `CharacterVitals`
- `WorldState`: 세계의 기존 canonical 상태
- `StoryMemory`: 장기 서사를 위한 제한된 비결정적 내러티브 문맥
- `Inventory`: 서버 소유 item/Equipment canonical state
- `CombatEncounter`: 전투 lifecycle, 참가자, current actor와 enemy state
- `AbilityState`: ability cooldown 상태
- `QuestState`: quest/objective progress와 typed `WorldFlag`/`EventFlag`
- `RelationshipState`: NPC identity, affinity, stage와 public/private narrative memory

운영 DB에는 `game_state_snapshot` JSON snapshot으로 저장합니다. `game_session`과 append-only `game_log`는 세션/턴 무결성과 committed-turn 원장 역할을 담당합니다.

## CharacterStats와 Skill Check

`PlayerCharacter.stats`는 임의 문자열 Map이 아니라 `MIGHT`, `AGILITY`, `INTELLECT`, `WILL`, `PRESENCE`를 가진 immutable `CharacterStats`입니다. 신규/legacy 기본 score는 10이고 stat score 1~30, d20 1~20, DC 1~40, situational modifier -20~20 범위를 검증합니다. 판정은 `rawRoll + statModifier + situationalModifier >= DC`이며 natural 1/20 특수 규칙은 없습니다.

`SkillCheckResult`는 계산 근거와 outcome/ruleset version을 보존합니다. production random은 `SecureRandom`, 테스트는 fixed/sequence `RandomSource`를 사용합니다. 실제 `/progress` vertical slice와 DB/UI projection까지 연결되어 있습니다.

## Inventory / Equipment

`GameState.inventory`는 stable owned item ID와 equipment slot 상태를 소유합니다. acquire/remove/quantity/consume/equip/unequip은 `InventoryCommand`와 순수 `InventoryRules`에서 canonical transition을 만듭니다. #42 이후 장착 item의 attack/damage modifier도 서버 전투 판정에 사용됩니다.

Narrative provider 응답은 inventory command 입력이 아니므로 story prose만으로 item을 만들거나 소비하거나 장착할 수 없습니다. 상세 invariant, audit, recovery는 [inventory-equipment.md](./inventory-equipment.md)를 기준으로 합니다.

## Vitals / Combat / Ability

`PlayerCharacter.vitals`는 HP/MP/status를 소유하고 `CombatEncounter`는 encounter lifecycle, enemy, turn order/current actor를 소유합니다. `COMBAT_ATTACK`, `COMBAT_ABILITY`, `COMBAT_PASS`, `COMBAT_ESCAPE`는 서버 규칙으로 resolve되며 attack roll/damage/mitigation과 ability MP/effect/cooldown도 provider 호출 전에 확정됩니다.

세부 규칙은 [vitals-status-effects.md](./vitals-status-effects.md), [combat-encounter.md](./combat-encounter.md), [ability-cooldown.md](./ability-cooldown.md)를 기준으로 합니다.

## Quest / Objective / World·Event Flag

#44 이후 `QuestState`는 quest runtime status, typed objective progress와 typed flag를 canonical state로 소유합니다. 최소 objective 형태는 `COUNT`, `BOOLEAN`, `STATE_MATCH`이고 collection/dialogue/combat fixture가 서버 action/state change를 관찰해 progress를 전이합니다.

Quest 완료/실패, objective progress, flag key/value/version은 provider prose가 아니라 `QuestRules`와 typed audit으로 결정됩니다. 상세 규칙은 [quest-objective-flags.md](./quest-objective-flags.md)를 기준으로 합니다.

## Story Memory

Story Memory는 canonical rule state를 복제하지 않고 장기 서사에 필요한 비결정적 기억만 보유합니다.

1. `canonicalFacts`: stable key, `sourceTurn`, `ACTIVE/SUPERSEDED` 상태를 가진 narrative fact. `inventory.*`, `player.vitals.*`, `quest.*`, `relationship.*`, `npc.*`, `world.flag.*`, `world.event.*`, `combat.*`, `ability.*` 등 GameState 소유 key는 저장할 수 없습니다.
2. `rollingSummary`: `sourceFromTurn`, `sourceToTurn`, `stateVersion`, `text`를 가진 구조화 요약입니다. summary 생성은 schema/range/version과 canonical-key 참조를 검증하고 최대 2회 bounded retry합니다. 실패하면 기존 StoryMemory와 canonical turn을 그대로 보존합니다.
3. `recentTurns`: 저장 자체는 turn 수로 자르지 않고 transcript를 보존하며, provider projection 시 명시적 token budget으로 최신 turn부터 선택합니다.

`NarrativeContext`는 inventory, player vitals/stats, quest/objective/World·Event Flag, NPC relationship/memory, combat, ability를 `GameState`에서 read-only canonical projection으로 만듭니다. StoryMemory projection은 canonical fact 512, summary 1024, recent turn 1600의 estimate budget을 각각 적용하며 총 memory budget 3136을 넘지 않습니다. summary source도 별도 budget으로 제한합니다.

충돌 시 우선순위는 `GameResult / canonical state > canonicalFacts > rollingSummary > recentTurns > LLM 생성 내용`입니다. summary가 미래 state version을 가리키면 projection에서 제외하고, summary 생성 응답이 state-owned canonical key를 참조하면 모순 가능성이 있는 응답으로 거절해 재시도합니다. 자연어 전체를 의미 분석해 사실 검증하는 것은 이 경계의 책임이 아닙니다.

## 현재 행동 경계

서버 발급 `AvailableAction`은 현재 turn과 token/type/arguments를 묶습니다. `ActionResolver`가 처리하는 현재 action type은 `NARRATIVE_CHOICE`, `SKILL_CHECK`, `COMBAT_ATTACK`, `COMBAT_ABILITY`, `COMBAT_PASS`, `COMBAT_ESCAPE`입니다. Inventory/Vitals command는 같은 pure resolution 경계에서 서버가 주입하는 typed effect이며 QuestRules는 resolution 결과를 관찰해 canonical quest/flag 전이를 추가합니다.

Narrative provider에는 raw state/action 문자열 대신 확정된 `GameResult`와 canonical next-state에서 만든 provider-safe `NarrativeContext`를 전달합니다. 서버 발급 action token은 provider context에 포함하지 않습니다.

## 현재 턴 처리 흐름

1. idempotency와 `(session_id, expected_turn)` reservation을 확인합니다.
2. 현재 세션/turn과 server-issued action payload를 검증합니다.
3. 필요한 Skill Check/Attack 판정과 typed command를 서버에서 확정합니다.
4. `ActionResolver`가 `GameResult`와 canonical next `StateTransition`을 만듭니다.
5. `QuestRules`가 typed action/state change만 관찰해 quest/objective/flag 전이를 적용합니다.
6. 확정 결과와 canonical next state에서 provider-safe `NarrativeContext`를 구성합니다.
7. rate limit과 provider budget/attempt 경계를 확인합니다.
8. Narrative Engine이 확정 결과를 재판정하지 않고 story와 다음 choice 후보를 생성합니다.
9. story prose를 현재 turn의 `StoryMemory.recentTurns`에 부착합니다.
10. recent transcript가 summary trigger budget을 넘으면 best-effort summary 갱신을 시도합니다. 실패해도 canonical turn은 유지됩니다.
11. 서버가 다음 server-issued available actions를 구성합니다.
12. `GameTurnCommit`이 state transition, typed audit, narrative linkage를 canonical transaction에서 저장합니다.

세부 책임은 [action-resolution.md](./action-resolution.md), provider 계약은 [narrative-context.md](./narrative-context.md)를 기준으로 합니다.

## 기존 세션 호환성

현재 snapshot schema는 v10입니다. #31 이전 raw `GameState`는 logical v0으로 취급하고 `GameStateUpgrader`가 v1부터 v10까지 한 단계씩 deterministic하게 승격합니다.

v9 -> v10에서는 과거 StoryMemory의 canonical facts가 현재 GameState가 이미 소유하는 key인지 확인한 뒤 중복 facts를 제거하고 구조화 `StorySummary.empty()`로 전환합니다. 비 state-owned legacy fact는 `sourceTurn/status`를 안전하게 복구할 수 없으므로 추측하지 않고 명시적으로 실패합니다. 기존 rolling summary 문자열도 source range/state version을 안전하게 복구할 수 없으므로 현재 canonical 사실로 재해석하지 않습니다.

각 schema에서 의미가 없던 신규 aggregate는 안전한 baseline만 추가합니다. 과거 story prose나 legacy `WorldState.flags`에서 전투/ability/quest/relationship 의미를 추정하지 않습니다. 현재 v10의 필수 필드 누락이나 손상 값은 legacy로 간주해 기본값 처리하지 않고 명시적으로 실패합니다.

read 자체는 DB를 다시 쓰지 않고 다음 정상 canonical commit에서 최신 snapshot 형식으로 저장합니다. snapshotless recovery는 `GameLog`의 inventory/vitals/combat/ability/quest/relationship typed audit을 turn 순서대로 replay합니다.

세부 snapshot 내용은 [game-state-snapshot-evolution.md](./game-state-snapshot-evolution.md)를 기준으로 합니다.
