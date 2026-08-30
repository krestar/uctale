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
- 후속 #28~#31 persistence tests가 공통 support class만 상속해 같은 환경을 재사용할 수 있습니다.
- 테스트가 production Neon DB에 연결될 가능성을 구조적으로 줄입니다.

Testcontainers version은 BOM `2.0.5`, PostgreSQL image는 `postgres:17.6-alpine`으로 고정합니다. 버전 변경은 일반 dependency/image update처럼 PR에서 검증한 뒤 반영합니다.

## 테스트 경계

JUnit tag `postgres`를 경계로 사용합니다.

- `test`: `postgres` tag를 제외하고 기존 H2/unit suite를 실행합니다.
- `postgresIntegrationTest`: `postgres` tag만 실행합니다.
- PostgreSQL 테스트는 `PostgresIntegrationTestSupport`를 상속해 datasource/Flyway/JPA 설정을 공유합니다.

현재 baseline은 다음 production 의미론을 검증합니다.

- 빈 PostgreSQL에서 Flyway V1~V5 migration chain 적용
- Hibernate `ddl-auto=validate`가 migration 결과와 현재 entity mapping을 검증
- `uk_game_log_session_turn` unique constraint
- `GameSession`의 `@Version` optimistic locking

## 후속 이슈 규칙

#28~#31에서 PostgreSQL에 의존하는 constraint, migration, locking 또는 recovery 동작을 추가할 경우 새 harness를 만들지 말고 기존 `PostgresIntegrationTestSupport`를 재사용합니다.

#32에서는 이 기반 위에서 M2의 cross-feature concurrency/migration/recovery regression matrix를 완성합니다.

## 실패 진단

CI의 `Run PostgreSQL integration tests` step은 일반 backend test와 분리되어 있습니다. 따라서 실패 시 먼저 다음을 구분합니다.

1. container startup / Docker 문제
2. Flyway migration 실패
3. Hibernate schema validation 실패
4. PostgreSQL constraint / optimistic locking assertion 실패

Testcontainers와 Spring Boot가 container 및 datasource 초기화 로그를 남기며, 테스트 이름은 실패한 contract를 직접 나타내도록 유지합니다. DB password는 ephemeral fixture 값이며 production credential이나 application secret을 사용하지 않습니다.
