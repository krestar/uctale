# Inventory / Equipment canonical state

## 목적

아이템 획득·소비·수량·장착 상태는 story prose가 아니라 서버가 결정하는 canonical game state입니다. #39에서 도입한 타입 안전한 aggregate와 audit/recovery 경계는 현재 #42의 장비 전투 modifier와 #44의 collection objective에서도 사용됩니다.

## 도메인 모델

`GameState.inventory`는 `Inventory.items`와 `Equipment.slots`를 소유합니다. `ItemDefinition`은 stable definition ID, ownership type, optional equipment slot과 현재 전투 규칙이 사용하는 attack/damage modifier를 가질 수 있습니다.

- `STACK`: 같은 owned ID + 같은 definition이면 획득 수량을 합칠 수 있습니다.
- `INSTANCE`: owned ID가 개별 인스턴스를 식별하고 quantity는 항상 1입니다.

최소 equipment slot은 `MAIN_HAND`, `OFF_HAND`, `BODY`, `ACCESSORY`입니다. 한 item은 정의된 slot에만 장착할 수 있고 동일 owned item을 여러 slot이 참조할 수 없습니다.

## 서버 명령과 invariant

`InventoryCommand` / `InventoryRules`는 provider와 무관한 순수 도메인 경계이며 acquire/remove/change quantity/consume/equip/unequip을 지원합니다.

- 보유 item quantity는 항상 1 이상입니다.
- 0/음수 수량, 보유량 초과 소비, 존재하지 않는 item은 거절합니다.
- `INSTANCE` quantity는 1이고 임의 수량 변경/부분 소비를 허용하지 않습니다.
- item definition과 맞지 않는 equipment slot은 거절합니다.
- 사용 중 slot 덮어쓰기와 중복 item 참조를 거절합니다.
- 장착 중 item은 명시적 unequip 없이 제거하거나 전량 소비할 수 없습니다.
- stack quantity 합산 overflow는 명시적으로 실패합니다.

`ActionResolver`에 inventory command가 전달된 경우에만 해당 명령을 canonical next state에 적용합니다. Narrative provider 응답은 inventory command 입력 경로가 아니므로 story text만으로 item을 생성·소비·장착할 수 없습니다.

## 현재 연동

장착 item의 attack/damage modifier는 서버의 `COMBAT_ATTACK` 판정에서 사용되며 provider가 modifier를 계산하지 않습니다. Collection quest fixture는 서버가 확정한 inventory acquire/quantity increase state change를 관찰해 objective progress를 갱신합니다.

따라서 장비 전투 효과와 quest progress 모두 canonical inventory transition 이후의 typed 상태를 근거로 하며 story prose를 근거로 삼지 않습니다.

## GameResult / Narrative 경계

각 inventory 명령은 `ItemAcquired`, `ItemRemoved`, `ItemQuantityChanged`, `ItemConsumed`, `ItemEquipped`, `ItemUnequipped` typed state change를 남깁니다. 이 변화는 provider 호출 전에 확정되어 `NarrativeContext`에 전달되고 LLM은 이를 추가하거나 재판정할 수 없습니다.

## persistence / idempotency

`GameTurnCommit`은 inventory audit을 previous inventory에 replay한 결과가 next state와 정확히 일치하는지 검증합니다. `game_log.inventory_changes_json`에는 inventory state change만 저장하며, 같은 completed idempotency mutation은 규칙/commit을 다시 수행하지 않습니다. stale turn/owner도 session turn, reservation, optimistic lock, unique turn constraint에서 canonical commit을 할 수 없습니다.

## snapshot / legacy recovery

`GameState.inventory`는 schema v3에서 도입되었고 현재 schema v8에도 필수입니다.

- v0/v1/v2 snapshot은 deterministic read-time upgrade에서 빈 inventory를 명시적으로 추가합니다.
- 이후 schema upgrade는 기존 inventory를 재판정하지 않고 보존하며 v5→v6에서 기존 item의 combat modifier만 안전한 0/0 baseline으로 추가합니다.
- 현재 v8 snapshot에서 inventory 누락/손상은 기본값으로 숨기지 않고 실패합니다.
- legacy/opening `GameLog.inventory_changes_json = NULL`은 변화 없음입니다.
- snapshot이 없으면 `GameStateRecovery`가 빈 inventory에서 시작해 committed turn audit을 순서대로 replay합니다.
- story prose에서 과거 item 의미를 추측해 복구하지 않습니다.

## 후속 범위

상점/거래, 랜덤 loot table, 내구도/upgrade, frontend inventory UI는 별도 범위입니다. 기본 장비 attack/damage modifier의 combat 연동은 현재 main에 구현되어 있습니다.
