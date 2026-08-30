package com.uctale.uctale.persistence;

import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.RollbackException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PostgresPersistenceBaselineTest extends PostgresIntegrationTestSupport {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("truncate table game_mutation_request, image_asset, game_state_snapshot, game_log, game_session restart identity cascade");
    }

    @Test
    @DisplayName("빈 PostgreSQL에 production Flyway migration이 모두 적용된다")
    void productionMigrationChain_IsAppliedToPostgres() {
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied())
                .extracting(info -> info.getVersion().getVersion())
                .contains("1", "2", "3", "4", "5", "6", "7");

        assertThat(tableExists("game_session")).isTrue();
        assertThat(tableExists("game_log")).isTrue();
        assertThat(tableExists("game_state_snapshot")).isTrue();
        assertThat(tableExists("image_asset")).isTrue();
        assertThat(tableExists("game_mutation_request")).isTrue();
    }

    @Test
    @DisplayName("PostgreSQL이 동일 session turn의 중복 GameLog를 거부한다")
    void gameLogTurnUniqueConstraint_IsEnforcedByPostgres() {
        Long sessionId = insertSession();
        insertGameLog(sessionId, 1);

        assertThatThrownBy(() -> insertGameLog(sessionId, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL에서 GameSession optimistic locking이 stale update를 거부한다")
    void gameSessionOptimisticLocking_IsEnforcedByPostgres() {
        Long sessionId = insertSession();

        EntityManager firstEntityManager = entityManagerFactory.createEntityManager();
        EntityManager secondEntityManager = entityManagerFactory.createEntityManager();
        try {
            GameSession first = firstEntityManager.find(GameSession.class, sessionId);
            GameSession stale = secondEntityManager.find(GameSession.class, sessionId);

            firstEntityManager.getTransaction().begin();
            first.advanceTurn();
            firstEntityManager.getTransaction().commit();

            secondEntityManager.getTransaction().begin();
            stale.advanceTurn();

            assertThatThrownBy(() -> secondEntityManager.getTransaction().commit())
                    .isInstanceOf(RollbackException.class);
        } finally {
            rollbackIfActive(firstEntityManager);
            rollbackIfActive(secondEntityManager);
            firstEntityManager.close();
            secondEntityManager.close();
        }

        Integer currentTurn = jdbcTemplate.queryForObject(
                "select current_turn from game_session where id = ?",
                Integer.class,
                sessionId
        );
        Long version = jdbcTemplate.queryForObject(
                "select version from game_session where id = ?",
                Long.class,
                sessionId
        );

        assertThat(currentTurn).isEqualTo(2);
        assertThat(version).isEqualTo(1L);
    }

    private Long insertSession() {
        return jdbcTemplate.queryForObject(
                """
                insert into game_session (
                    owner_key, world_setting, character_setting, is_game_over, current_turn, version
                ) values (?, ?, ?, false, 1, 0)
                returning id
                """,
                Long.class,
                OWNER_KEY,
                "세계관",
                "캐릭터"
        );
    }

    private void insertGameLog(Long sessionId, int turnNumber) {
        jdbcTemplate.update(
                """
                insert into game_log (
                    session_id, turn_number, previous_state_version, state_version, story_text, choices_json
                ) values (?, ?, ?, ?, ?, ?)
                """,
                sessionId,
                turnNumber,
                turnNumber - 1,
                turnNumber,
                "이야기",
                "[]"
        );
    }

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select to_regclass(?) is not null",
                Boolean.class,
                "public." + tableName
        ));
    }

    private void rollbackIfActive(EntityManager entityManager) {
        if (entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().rollback();
        }
    }
}
