# 게임 세션 소유권 정책

## 목적

공유 베타의 접근 비밀번호는 애플리케이션 진입을 제한할 뿐, 개별 `GameSession`의 소유자를 구분하지 못합니다. 이 문서는 접근 세션에서 파생한 owner key를 게임 세션에 연결하고, session ID를 아는 다른 접근 주체가 조회·진행하지 못하도록 하는 정책을 정의합니다.

## Owner key와 token 책임

- 공유 비밀번호 인증과 사용자 계정은 동일한 개념이 아닙니다.
- 비밀번호 인증 성공 시 브라우저별 랜덤 owner key를 발급합니다.
- owner key는 `uctale_owner` HttpOnly 쿠키의 서명된 owner token에 저장합니다.
- 단기 `uctale_access` 쿠키에도 같은 owner key를 포함합니다.
- access token `v1.{expiresAt}.{ownerKey}.{signature}`은 보호 API 호출 자격을 약 1시간 유지하는 단기 credential입니다.
- owner token `o2.{expiresAt}.{ownerKey}.{signature}`은 재인증 뒤에도 같은 저장 세션 소유권을 복원하기 위한 장기 credential입니다.
- 두 token 모두 서버가 payload의 `expiresAt`을 직접 검증합니다. `expiresAt <= now`이면 만료입니다.
- owner token의 기본 TTL은 180일이며 기존 `GAME_OWNER_COOKIE_TTL_SECONDS` 하나가 owner token 만료와 owner cookie `Max-Age`에 동일하게 사용됩니다. 기존 환경변수 이름은 배포 호환성을 위해 유지하지만 의미는 cookie-only TTL이 아니라 owner credential 전체 TTL입니다.
- 재인증 시 유효한 owner token이 있으면 같은 owner key를 재사용하고 새 `o2` token으로 TTL을 갱신합니다.
- 유효한 access token이 남아 있는데 owner cookie가 없거나 유효하지 않으면 보호 API 첫 요청에서 access token의 owner key로 새 `o2` owner cookie를 발급합니다.

owner key 자체는 API 응답, URL, Web Storage에 노출하지 않습니다.

## Legacy `o1` migration

기존 `o1.{ownerKey}.{signature}` token에는 발행 시각과 만료 시각이 없으므로 개별 token의 나이를 안전하게 복구할 수 없습니다. 따라서 현재 시각이나 배포일을 기준으로 임의의 발행 시각을 추측하지 않습니다.

기본 정책은 `GAME_OWNER_LEGACY_O1_ACCEPT_UNTIL_EPOCH_SECONDS=0`이며, 이 경우 모든 `o1` token을 owner identity 복원 근거로 거부합니다. 유효한 단기 access token이 남은 클라이언트는 앞의 보호 API 승격 경로를 통해 같은 owner key의 `o2` token을 받을 수 있습니다.

운영상 기존 `o1`을 제한된 기간 동안 승격해야 한다면 `GAME_OWNER_LEGACY_O1_ACCEPT_UNTIL_EPOCH_SECONDS`에 절대 Unix epoch seconds cutoff를 명시적으로 설정합니다.

- cutoff 이전에는 서명과 owner key 형식이 유효한 `o1`만 owner key 복원에 사용할 수 있습니다.
- 재인증 성공 시 즉시 `o2` token으로 교체합니다.
- `now >= cutoff`부터는 `o1`을 거부합니다.
- 설정값이 0이면 migration window는 비활성화됩니다.

이 정책은 발행 시각이 없는 legacy credential에 임의의 개별 만료 시각을 부여하지 않고, 운영자가 명시적으로 정한 migration 종료 경계만 사용합니다.

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

- 새 `o2` owner token이 서버 만료 경계를 직접 검증하는지 확인합니다.
- 만료 직전/경계 시각, tampering, malformed token을 deterministic clock으로 검증합니다.
- 기본 `o1` 거부와 명시적 cutoff migration 경계를 검증합니다.
- 만료된 owner token 재인증이 기존 owner key를 재사용하지 않는지 확인합니다.
- 기존 access token에서 `o2` owner cookie를 승격하는 호환 경로를 검증합니다.
- 새 세션에 생성 owner가 저장되는지 persistence integration test로 검증합니다.
- 같은 owner는 최신 턴을 조회·진행할 수 있는지 검증합니다.
- 다른 owner는 동일 session ID에 대해 404를 받는지 API/persistence 양쪽에서 검증합니다.
- 저장 직전 owner 재검증이 누락되지 않는지 service/persistence 테스트로 검증합니다.
