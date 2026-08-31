# PostgreSQL integration tests

UCTale의 기본 backend test suite는 빠른 회귀 검증을 위해 H2를 사용합니다. M2부터 persistence 계약을 변경하는 작업은 production PostgreSQL 의미론을 별도 integration suite에서 함께 검증합니다.

## 실행 명령

기본 unit / H2 suite:

```bash
./gradlew clean test
```

PostgreSQL integration suite:

```bash
./gradlew postgresIntegrationTest
```

PostgreSQL suite는 Testcontainers를 사용하므로 로컬에서 Docker-compatible container runtime이 실행 중이어야 합니다. production Neon DB나 별도 shared test DB에는 연결하지 않습니다.

GitHub Actions도 같은 `./gradlew postgresIntegrationTest` 명령을 사용합니다. GitHub-hosted Ubuntu runner의 Docker daemon에서 ephemeral PostgreSQL container를 띄우므로 별도 database secret은 필요하지 않습니다.

## 선택한 방식

#73에서는 GitHub Actions의 PostgreSQL service container 대신 Testcontainers를 선택했습니다.

이유:

- 로컬과 CI가 동일한 Java test code와 동일한 database image를 사용합니다.
- 포트, username, password를 CI workflow와 로컬 설정에 중복 정의하지 않습니다.
- 후속 persistence tests가 공통 support class만 상속해 같은 환경을 재사용할 수 있습니다.
- 테스트가 production Neon DB에 연결될 가능성을 구조적으로 줄입니다.

Testcontainers version은 BOM `2.0.5`, PostgreSQL image는 `postgres:17.6-alpine`으로 고정합니다. 버전 변경은 일반 dependency/image update처럼 PR에서 검증한 뒤 반영합니다.

## 테스트 경계

JUnit tag `postgres`를 경계로 사용합니다.

- `test`: `postgres` tag를 제외하고 기존 H2/unit suite를 실행합니다.
- `postgresIntegrationTest`: `postgres` tag만 실행합니다.
- PostgreSQL 테스트는 `PostgresIntegrationTestSupport`를 상속해 datasource/Flyway/JPA 설정을 공유합니다.

현재 baseline은 다음 production 의미론을 검증합니다.

- 빈 PostgreSQL에서 Flyway V1~V8 production migration chain 순차 적용
- Hibernate `ddl-auto=validate`가 migration 결과와 현재 entity mapping을 검증
- `uk_game_log_session_turn` unique constraint
- `GameSession`의 `@Version` optimistic locking
- owner 범위 `Idempotency-Key` unique constraint와 fingerprint conflict
- `(session_id, expected_turn)` turn reservation unique/lease takeover 의미론
- versioned `GameState` snapshot의 legacy 호환 및 latest schema round-trip

## M2 regression matrix

#32의 종료 게이트는 기존 개별 persistence 테스트를 제거하지 않고, cross-feature 경계를 `PostgresM2TurnIntegrityMatrixTest`와 legacy migration fixture로 추가 고정합니다.

| 계약 | PostgreSQL 검증 |
| --- | --- |
| `/init` completed retry | 동일 key 재시도는 저장된 session/turn을 재사용하고 provider 및 새 session 생성을 반복하지 않음 |
| `/progress` completed retry | 동일 key 재시도는 저장 결과를 재사용하고 canonical GameLog/snapshot을 다시 쓰지 않음 |
| 동일 key + 다른 payload | fingerprint mismatch를 provider 진입 전에 conflict로 거부 |
| 같은 session/turn 최초 동시 요청 | active reservation은 하나만 유지되고 유효 lease 동안 provider 진입도 하나로 제한 |
| crash + lease expiry | deterministic clock으로 takeover를 재현하고 stale reservation owner의 commit을 차단 |
| canonical commit | `GameSession.currentTurn`, latest `GameLog.stateVersion`, snapshot turn, mutation request result turn이 같은 값으로 수렴 |
| legacy production 형태 | V5 fixture를 V8까지 migration한 뒤 ledger 데이터와 frozen v0 snapshot이 복구 가능함을 검증 |

동시성 케이스는 짧은 wall-clock timeout이나 `Thread.sleep`에 의존하지 않습니다. 시작 barrier와 deterministic clock을 사용하고, fake provider 호출 횟수를 직접 세어 race 조건을 관찰합니다. 같은 session/turn 최초 요청 케이스는 반복 실행해 scheduling 순서가 바뀌어도 계약이 유지되는지 검증합니다.

## provider exactly-once 경계

turn reservation은 **유효한 lease 동안** 중복 provider 호출을 억제하고 canonical DB commit을 하나로 제한합니다. 하지만 provider 호출 성공 직후 프로세스가 죽고 lease가 만료되면 takeover 요청이 provider를 다시 호출할 수 있습니다.

따라서 M2가 보장하는 것은 다음과 같습니다.

- active lease 동안 duplicate provider suppression
- stale reservation owner의 canonical commit 차단
- canonical `GameSession` / `GameLog` / snapshot / completed request 결과는 단일 commit으로 수렴

반대로 외부 provider에 대한 strict exactly-once 호출은 보장하지 않습니다. crash/takeover 테스트는 이 한계를 숨기지 않고 provider 호출이 두 번 발생할 수 있음을 명시적으로 assert합니다.

## 실패 진단

CI의 `Run PostgreSQL integration tests` step은 일반 backend test와 분리되어 있습니다. 따라서 실패 시 먼저 다음을 구분합니다.

1. container startup / Docker 문제
2. Flyway migration 및 실패 version/schema
3. Hibernate schema validation 실패
4. PostgreSQL unique / optimistic locking assertion 실패
5. idempotency request key/fingerprint 단계 실패
6. reservation session/turn/attempt 단계 실패
7. canonical `currentTurn` / ledger stateVersion / snapshot / request resultTurn 불일치

M2 matrix assertion은 가능한 경우 `phase`, `request`, `session`, `turn` 또는 migration `schema` 문맥을 함께 출력합니다. Testcontainers와 Spring Boot가 container 및 datasource 초기화 로그를 남기며, DB password는 ephemeral fixture 값이고 production credential이나 application secret을 사용하지 않습니다.
