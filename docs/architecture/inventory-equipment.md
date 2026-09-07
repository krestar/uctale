# Inventory / Equipment canonical state

## 목적

아이템 획득·소비·수량·장착 상태는 story prose가 아니라 서버가 결정하는 canonical game state입니다. #39는 전투/보상 규칙이 이 상태를 사용할 수 있도록 최소한의 타입 안전한 aggregate와 audit/recovery 경계를 정의합니다.

## 도메인 모델

`GameState.inventory`는 다음 두 부분을 소유합니다.

- `Inventory.items`: stable owned item ID -> `OwnedItem`
- `Equipment.slots`: `EquipmentSlot` -> stable owned item ID

`ItemDefinition`은 stable definition ID, ownership type, optional equipment slot을 가집니다.

- `STACK`: 같은 owned ID + 같은 definition이면 획득 수량을 합칠 수 있습니다.
- `INSTANCE`: owned ID가 개별 인스턴스를 식별하고 quantity는 항상 1입니다.

최소 equipment slot은 `MAIN_HAND`, `OFF_HAND`, `BODY`, `ACCESSORY`입니다. 한 item은 정의된 slot에만 장착할 수 있고 동일 owned item을 여러 slot이 참조할 수 없습니다.

## 서버 명령과 invariant

`InventoryCommand` / `InventoryRules`는 provider와 무관한 순수 도메인 경계입니다.

지원 명령:

- acquire
- remove
- change quantity
- consume
- equip
- unequip

공통 invariant:

- 보유 item quantity는 항상 1 이상입니다.
- 0/음수 수량, 보유량 초과 소비, 존재하지 않는 item은 거절합니다.
- `INSTANCE` quantity는 1이고 임의 수량 변경/부분 소비를 허용하지 않습니다.
- item definition과 맞지 않는 equipment slot은 거절합니다.
- 사용 중 slot 덮어쓰기와 중복 item 참조를 거절합니다.
- 장착 중 item은 명시적 unequip 없이 제거하거나 전량 소비할 수 없습니다.
- stack quantity 합산 overflow는 명시적으로 실패합니다.

`ActionResolver`에 inventory command가 전달된 경우에만 해당 명령을 canonical next state에 적용합니다. 현재 Narrative provider 응답은 inventory command 입력 경로가 아니므로 story text만으로 item을 생성·소비·장착할 수 없습니다.

## GameResult / Narrative 경계

각 inventory 명령은 결과를 `GameResult.StateChange`로 남깁니다.

- `ItemAcquired`
- `ItemRemoved`
- `ItemQuantityChanged`
- `ItemConsumed`
- `ItemEquipped`
- `ItemUnequipped`

이 state change는 provider 호출 전에 확정되고 기존 `NarrativeContext`에 전달됩니다. LLM은 이를 서술할 수 있지만 state change를 추가하거나 다시 판정할 수 없습니다.

## persistence / idempotency

`GameTurnCommit`은 전달된 inventory state change를 previous inventory에 replay한 결과가 `StateTransition.nextState.inventory`와 정확히 일치하는지 검증합니다. 누락되거나 변조된 audit으로 inventory가 바뀐 transition은 commit할 수 없습니다.

최종 canonical transaction은 기존과 동일하게 다음을 함께 처리합니다.

- session turn advance
- append-only `GameLog`
- 최신 `GameStateSnapshot`
- mutation completion
- reservation release

`game_log.inventory_changes_json`에는 inventory state change만 JSON audit으로 저장합니다. `TurnAdvanced` 등 다른 state change는 이 컬럼에 중복 저장하지 않습니다.

같은 completed idempotency mutation은 기존 `game_mutation_request` replay 경계를 사용하므로 provider/규칙/commit을 다시 수행하지 않습니다. stale turn/owner도 기존 session turn, reservation, optimistic lock, unique turn constraint에서 canonical commit을 할 수 없습니다.

## snapshot / legacy recovery

snapshot schema v3부터 `GameState.inventory`가 필수입니다.

- v0/v1/v2 snapshot은 deterministic read-time upgrade에서 빈 inventory를 명시적으로 추가합니다.
- 현재 v3 snapshot에서 inventory 누락은 손상 데이터로 실패합니다.
- legacy/opening `GameLog.inventory_changes_json = NULL`은 변화 없음입니다.
- snapshot이 없으면 `GameStateRecovery`가 빈 inventory에서 시작해 각 committed turn의 inventory audit을 순서대로 replay합니다.
- story prose에서 과거 item 의미를 추측해 복구하지 않습니다.

## 범위 밖

#39에서는 상점/거래, 랜덤 loot table, 내구도/upgrade, 전투 modifier, frontend inventory UI를 구현하지 않습니다.
