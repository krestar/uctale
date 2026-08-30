# 게임 세션 소유권 정책

## 목적

공유 베타의 접근 비밀번호는 애플리케이션 진입을 제한할 뿐, 개별 `GameSession`의 소유자를 구분하지 못합니다. 이 문서는 접근 세션에서 파생한 owner key를 게임 세션에 연결하고, session ID를 아는 다른 접근 주체가 조회·진행하지 못하도록 하는 정책을 정의합니다.

## Owner key

- 공유 비밀번호 인증과 사용자 계정은 동일한 개념이 아닙니다.
- 비밀번호 인증 성공 시 브라우저별 랜덤 owner key를 발급합니다.
- owner key는 `uctale_owner` HttpOnly 쿠키에 HMAC 서명된 형태로 저장합니다.
- 단기 `uctale_access` 쿠키에도 같은 owner key를 포함합니다.
- 접근 쿠키는 기본 1시간, owner 쿠키는 기본 180일 유지합니다.
- 재인증 시 유효한 owner 쿠키가 있으면 같은 owner key를 재사용합니다.
- #24에서 발급된 기존 access 쿠키만 있는 경우 보호 API 첫 요청에서 access token의 nonce를 owner key로 승격해 owner 쿠키를 발급합니다.

owner key 자체는 API 응답, URL, Web Storage에 노출하지 않습니다.

## GameSession 소유권

새 `GameSession`은 생성 시 `owner_key`를 반드시 저장합니다. session-scoped persistence 조회는 `sessionId` 단독 조회를 사용하지 않고 `sessionId + ownerKey` 조건을 사용합니다.

진행 요청은 다음 두 시점 모두 소유권을 검사합니다.

1. Narrative 호출 전 최신 턴을 불러올 때
2. Narrative 호출 후 다음 턴을 저장하기 직전 세션을 다시 읽을 때

따라서 service 단계에서 확인한 소유권이 persistence 저장 단계에서 누락되지 않습니다.

## 404 / 403 정책

- 유효한 접근 세션이 없으면 기존 접근 제어 정책대로 401을 반환합니다.
- 유효한 접근 세션이지만 보호 client header가 없으면 403을 반환합니다.
- session ID가 존재하지 않는 경우와 다른 owner의 session ID인 경우는 모두 `SESSION_NOT_FOUND` 404로 반환합니다.

다른 사용자가 session ID의 존재 여부를 구분할 수 없도록 소유권 불일치를 별도 403으로 노출하지 않습니다.

## 기존 익명 세션 migration

V3 migration은 기존 `game_session` 행에 `legacy-{id}` owner key를 백필한 뒤 `owner_key`를 NOT NULL로 고정합니다.

이 값은 실제 owner cookie가 허용하는 랜덤 Base64URL 형식과 의도적으로 호환되지 않습니다. 따라서 기존 익명 세션은 배포 후 누구도 새 owner identity로 선점할 수 없으며 읽기/진행이 차단됩니다.

공유 베타 단계에서 영구 사용자 계정이나 세션 복구 UI가 없으므로, 보안상 기존 익명 세션의 접근을 끊는 것을 안전한 migration 정책으로 선택합니다.

## 테스트 기준

- 새 세션에 생성 owner가 저장되는지 persistence integration test로 검증합니다.
- 같은 owner는 최신 턴을 조회·진행할 수 있는지 검증합니다.
- 다른 owner는 동일 session ID에 대해 404를 받는지 API/persistence 양쪽에서 검증합니다.
- 저장 직전 owner 재검증이 누락되지 않는지 service/persistence 테스트로 검증합니다.
- 기존 access token에서 owner 쿠키를 승격하는 호환 경로와 재로그인 시 owner 유지 동작을 검증합니다.
