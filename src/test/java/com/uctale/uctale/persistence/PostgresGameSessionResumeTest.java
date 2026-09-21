package com.uctale.uctale.persistence;

import com.uctale.uctale.application.game.ChoiceCodec;
import com.uctale.uctale.application.game.GameMutationRequestService;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.GameSessionQueryService;
import com.uctale.uctale.dto.GameChoice;
import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PostgresGameSessionResumeTest extends PostgresIntegrationTestSupport {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Autowired private GamePersistenceService persistenceService;
    @Autowired private GameSessionQueryService queryService;
    @Autowired private GameMutationRequestService mutationRequestService;
    @Autowired private ChoiceCodec choiceCodec;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                truncate table game_turn_reservation, game_mutation_request, image_asset,
                    game_state_snapshot, game_log, game_session restart identity
                """);
    }

    @Test
    @DisplayName("PostgreSQL 저장 세션은 마지막 완료 turn의 story canonical state actions를 함께 복원한다")
    void completedTurn_IsResumedFromPostgresConsistently() {
        var session = persistenceService.saveOpening(
                OWNER_KEY,
                "안개 낀 항구 도시",
                "기억을 잃은 항해사",
                "부두에서 눈을 떴다.",
                choiceCodec.serialize(List.of(new GameChoice(1, "등대를 향한다"))),
                null
        );

        var summaries = queryService.listSessions(OWNER_KEY);
        var resumed = queryService.resumeSession(OWNER_KEY, session.getId());

        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().status()).isEqualTo("READY");
        assertThat(summaries.getFirst().canResume()).isTrue();
        assertThat(resumed.status()).isEqualTo("READY");
        assertThat(resumed.turnNumber()).isEqualTo(1);
        assertThat(resumed.canonicalStateTurn()).isEqualTo(1);
        assertThat(resumed.game()).isNotNull();
        assertThat(resumed.game().turnNumber()).isEqualTo(1);
        assertThat(resumed.game().storyText()).isEqualTo("부두에서 눈을 떴다.");
        assertThat(resumed.game().choices()).extracting(GameChoice::text).containsExactly("등대를 향한다");
    }

    @Test
    @DisplayName("만료 lease와 provider cooldown은 FAILED와 RECOVERY_WAIT 상태로 구분된다")
    void expiredLeaseAndProviderCooldown_AreReportedAsDistinctRecoveryStates() {
        var session = persistenceService.saveOpening(
                OWNER_KEY, "세계", "인물", "완료된 이야기",
                choiceCodec.serialize(List.of(new GameChoice(1, "진행한다"))), null
        );
        var mutation = mutationRequestService.begin(
                OWNER_KEY,
                GameMutationRequestService.PROGRESS,
                "resume-expired-001",
                session.getId(),
                1,
                "a".repeat(64)
        );

        jdbcTemplate.update(
                "update game_turn_reservation set lease_expires_at = current_timestamp - interval '1 second' where request_id = ?",
                mutation.requestId()
        );

        var expired = queryService.resumeSession(OWNER_KEY, session.getId());
        assertThat(expired.status()).isEqualTo("FAILED");
        assertThat(expired.retryable()).isTrue();
        assertThat(expired.canProgress()).isTrue();
        assertThat(expired.game()).isNotNull();

        jdbcTemplate.update(
                """
                update game_turn_reservation
                set provider_attempt_count = 3,
                    recovery_available_at = current_timestamp + interval '30 seconds'
                where request_id = ?
                """,
                mutation.requestId()
        );

        var waiting = queryService.resumeSession(OWNER_KEY, session.getId());
        assertThat(waiting.status()).isEqualTo("RECOVERY_WAIT");
        assertThat(waiting.retryable()).isTrue();
        assertThat(waiting.canProgress()).isFalse();
        assertThat(waiting.retryAfterSeconds()).isPositive();
        assertThat(waiting.game()).isNotNull();

        jdbcTemplate.update(
                "update game_turn_reservation set recovery_available_at = current_timestamp - interval '1 second' where request_id = ?",
                mutation.requestId()
        );

        var recoverable = queryService.resumeSession(OWNER_KEY, session.getId());
        assertThat(recoverable.status()).isEqualTo("FAILED");
        assertThat(recoverable.retryable()).isTrue();
        assertThat(recoverable.canProgress()).isTrue();
    }

    @Test
    @DisplayName("지원하지 않는 snapshot은 기본값으로 덮지 않고 UNRECOVERABLE로 격리한다")
    void unsupportedSnapshot_IsNotSilentlyRecovered() {
        var session = persistenceService.saveOpening(
                OWNER_KEY, "세계", "인물", "완료된 이야기",
                choiceCodec.serialize(List.of(new GameChoice(1, "진행한다"))), null
        );
        jdbcTemplate.update(
                "update game_state_snapshot set state_json = ? where session_id = ?",
                "{\"schemaVersion\":999,\"rulesetVersion\":1,\"state\":{}}",
                session.getId()
        );

        var summary = queryService.listSessions(OWNER_KEY).getFirst();
        var resumed = queryService.resumeSession(OWNER_KEY, session.getId());

        assertThat(summary.status()).isEqualTo("UNRECOVERABLE");
        assertThat(summary.canResume()).isFalse();
        assertThat(resumed.status()).isEqualTo("UNRECOVERABLE");
        assertThat(resumed.canProgress()).isFalse();
        assertThat(resumed.game()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "select state_json from game_state_snapshot where session_id = ?",
                String.class,
                session.getId()
        )).contains("\"schemaVersion\":999");
    }
}
