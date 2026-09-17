# Session list and resume

UCTale은 별도 save slot을 만들지 않는다. 각 게임의 저장 단위는 기존 canonical 완료 turn이며, session list/resume은 `GameSession`, append-only `GameLog`, `GameStateSnapshot`, mutation reservation을 read-only projection한다.

## HTTP contract

모든 endpoint는 기존 access session/owner 정책을 사용한다.

- `GET /api/game/sessions`
  - 현재 owner의 session만 `updatedAt` 내림차순으로 반환한다.
  - 최소 metadata: `sessionId`, `title`, `currentTurn`, `updatedAt`, `status`, `statusMessage`, `retryable`, `canResume`, `thumbnailUrl`.
- `GET /api/game/sessions/{sessionId}`
  - 다른 owner의 session은 기존 ownership 정책과 동일하게 찾을 수 없는 session으로 처리한다.
  - 마지막 완료 turn의 story, choices, image와 canonical state projection을 함께 반환한다.
  - `turnNumber`, `canonicalStateTurn`, `game.turnNumber`는 같은 완료 turn을 가리켜야 한다.

조회 endpoint는 Narrative/Image provider를 호출하지 않는다.

## Consistency boundary

Session resume은 다음 값이 모두 같은 완료 turn을 가리킬 때만 playable payload를 반환한다.

1. `GameSession.currentTurn`
2. 최신 `GameLog.turnNumber/stateVersion`
3. deserialize/upgrade된 `GameState.turnNumber`
4. 최신 `GameLog.choicesJson`

여러 row를 읽는 동안 progress commit이 끼어 서로 다른 시점의 값을 섞지 않도록 query service는 `REPEATABLE_READ` read transaction을 사용한다.

Snapshot이 없으면 기존 `GameStateRecovery`로 append-only log를 replay한다. 지원하지 않는 schema/ruleset, 손상 snapshot, 복구 불가능한 ledger는 현재 규칙으로 추측하거나 DB를 자동 수정하지 않고 `UNRECOVERABLE`로 격리한다.

## Recovery states

- `READY`: 마지막 완료 turn에서 새 선택을 시작할 수 있다.
- `PROCESSING`: 현재 turn에 유효한 lease가 있다. 마지막 완료 turn은 표시하지만 새 progress는 잠근다.
- `FAILED`: 진행 요청이 완료되지 않았다. lease가 만료되고 provider retry budget이 남아 있으면 마지막 완료 turn에서 다시 진행할 수 있다. provider attempt 한도에 도달했다면 `retryable=false`, `canProgress=false`다.
- `UNRECOVERABLE`: canonical snapshot/log/actions를 안전하게 같은 turn으로 구성할 수 없다. 저장 데이터는 변경하지 않으며 resume progress를 허용하지 않는다.

유효 lease에서 provider attempt count가 최대치인 경우에도 마지막 provider attempt가 실제 실행 중일 수 있으므로 lease가 유효한 동안은 `PROCESSING`이 우선한다.

## Autosave meaning

새 save operation을 추가하지 않는다. Opening과 각 progress가 canonical commit을 완료한 시점이 자동 저장 완료 시점이다. 처리 중이거나 실패한 request/reservation은 canonical `GameLog`를 만들지 않으므로 session UI는 항상 마지막 완료 turn까지만 노출한다.

## Owner / device boundary

Session list/resume은 #25에서 도입한 `ownerKey` 경계를 그대로 사용한다. 같은 owner identity가 복원된 클라이언트는 새로고침 후에도 완료 turn부터 재개할 수 있다.

현재 owner identity는 장기 HttpOnly owner cookie에 연결되어 있다. 물리적으로 다른 기기로 owner identity를 이전하는 계정, 공유 링크, export/import 기능은 이 이슈 범위에 포함하지 않는다.
