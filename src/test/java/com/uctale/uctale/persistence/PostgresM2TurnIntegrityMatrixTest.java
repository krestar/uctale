package com.uctale.uctale.persistence;

import com.uctale.uctale.application.game.GameMutationRequestService;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.GameTurnCommit;
import com.uctale.uctale.application.game.IdempotencyConflictException;
import com.uctale.uctale.application.game.MutationInProgressException;
import com.uctale.uctale.application.game.TurnConflictException;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.repository.GameMutationRequestRepository;
import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PostgresM2TurnIntegrityMatrixTest extends PostgresIntegrationTestSupport {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String OPENING_CHOICES = "[{\"id\":1,\"text\":\"문을 연다\"}]";
    private static final String NEXT_CHOICES = "[{\"id\":2,\"text\":\"계속 간다\"}]";
    private static final int LEASE_SECONDS = 30;

    @Autowired private GamePersistenceService persistenceService;
    @Autowired private GameMutationRequestRepository mutationRequestRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private MutableClock clock;
    private GameMutationRequestService mutationService;
    private TransactionTemplate transactionTemplate;
    private FakeNarrativeProvider provider;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                truncate table game_turn_reservation, game_mutation_request, image_asset,
                    game_state_snapshot, game_log, game_session restart identity
                """);
        clock = new MutableClock(Instant.parse("2026-08-31T04:00:00Z"));
        mutationService = new GameMutationRequestService(
                mutationRequestRepository, jdbcTemplate, clock, LEASE_SECONDS
        );
        transactionTemplate = new TransactionTemplate(transactionManager);
        provider = new FakeNarrativeProvider();
    }

    @Test
    @DisplayName("완료된 init retry는 provider와 새 session을 만들지 않고 canonical opening을 재사용한다")
    void completedInitRetry_ReusesCanonicalOpeningExactlyOnce() {
        String key = "matrix-init-key-001";
        String fingerprint = "i".repeat(64);

        BeginAttempt first = beginInitAttempt(key, fingerprint);
        assertThat(first.error()).as("phase=init-begin request=%s", key).isNull();

        String openingStory = provider.nextStory();
        GameSession session = persistenceService.saveOpening(
                OWNER_KEY,
                "matrix-world",
                "matrix-character",
                openingStory,
                OPENING_CHOICES,
                null,
                first.result().requestId(),
                "matrix-opening"
        );

        BeginAttempt retry = beginInitAttempt(key, fingerprint);
        assertThat(retry.error()).as("phase=init-retry request=%s", key).isNull();
        assertThat(retry.result().replay()).isTrue();
        assertThat(retry.result().resultSessionId()).isEqualTo(session.getId());
        assertThat(retry.result().resultTurn()).isEqualTo(1);

        BeginAttempt conflictingPayload = beginInitAttempt(key, "j".repeat(64));
        assertThat(conflictingPayload.error())
                .as("phase=init-fingerprint-conflict request=%s", key)
                .isInstanceOf(IdempotencyConflictException.class);

        assertThat(provider.calls()).as("provider calls for completed init retry").isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from game_session", Integer.class))
                .as("canonical session count after init retry")
                .isEqualTo(1);
        assertCanonicalTurn(session.getId(), key, 1, 1);
    }

    @Test
    @DisplayName("완료된 progress retry는 provider와 canonical state를 재실행하지 않고 저장 결과를 재사용한다")
    void completedProgressRetry_ReusesCanonicalResultExactlyOnce() {
        GameSession session = createOpening();
        String key = "matrix-retry-key-001";
        String fingerprint = "a".repeat(64);

        BeginAttempt first = beginProgressAttempt(session.getId(), key, fingerprint);
        assertThat(first.error()).as(context("begin", key, session.getId(), 1)).isNull();

        String story = provider.nextStory();
        commitTurn(session.getId(), first.result(), story);

        BeginAttempt retry = beginProgressAttempt(session.getId(), key, fingerprint);
        assertThat(retry.error()).as(context("retry", key, session.getId(), 1)).isNull();
        assertThat(retry.result().replay()).isTrue();
        assertThat(retry.result().resultSessionId()).isEqualTo(session.getId());
        assertThat(retry.result().resultTurn()).isEqualTo(2);

        BeginAttempt conflictingPayload = beginProgressAttempt(session.getId(), key, "b".repeat(64));
        assertThat(conflictingPayload.error())
                .as(context("fingerprint-conflict", key, session.getId(), 1))
                .isInstanceOf(IdempotencyConflictException.class);

        assertThat(provider.calls()).as("provider calls for completed progress retry").isEqualTo(1);
        assertCanonicalTurn(session.getId(), key, 2, 2);
    }

    @RepeatedTest(3)
    @DisplayName("같은 session/turn 최초 동시 요청은 active reservation과 provider 진입을 하나로 제한한다")
    void concurrentFirstRequests_AllowSingleActiveProviderExecution() throws Exception {
        GameSession session = createOpening();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ProviderAttempt> first = executor.submit(concurrentAttempt(
                    ready, start, session.getId(), "matrix-concurrent-key-a", "c".repeat(64)
            ));
            Future<ProviderAttempt> second = executor.submit(concurrentAttempt(
                    ready, start, session.getId(), "matrix-concurrent-key-b", "c".repeat(64)
            ));

            ready.await();
            start.countDown();

            List<ProviderAttempt> attempts = List.of(first.get(), second.get());
            List<ProviderAttempt> winners = attempts.stream().filter(ProviderAttempt::providerCalled).toList();
            List<ProviderAttempt> rejected = attempts.stream().filter(attempt -> !attempt.providerCalled()).toList();

            assertThat(winners).as("provider winner session=%d turn=1", session.getId()).hasSize(1);
            assertThat(rejected).as("reservation loser session=%d turn=1", session.getId()).hasSize(1);
            assertThat(rejected.getFirst().begin().error()).isInstanceOf(MutationInProgressException.class);
            assertThat(provider.calls()).as("provider calls while lease is active").isEqualTo(1);
            assertThat(reservationCount(session.getId(), 1)).as("active reservation rows").isEqualTo(1);

            ProviderAttempt winner = winners.getFirst();
            commitTurn(session.getId(), winner.begin().result(), winner.story());

            assertCanonicalTurn(session.getId(), winner.key(), 2, 2);
            assertThat(reservationCount(session.getId(), 1)).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("crash 후 lease takeover는 provider 재호출을 허용하지만 stale owner의 canonical commit은 차단한다")
    void expiredLeaseTakeover_AllowsProviderRetryButSingleCanonicalCommit() {
        GameSession session = createOpening();

        BeginAttempt crashed = beginProgressAttempt(session.getId(), "matrix-crash-key-a", "d".repeat(64));
        assertThat(crashed.error()).as(context("crashed-begin", "matrix-crash-key-a", session.getId(), 1)).isNull();
        String staleStory = provider.nextStory();

        clock.advance(Duration.ofSeconds(LEASE_SECONDS + 1));

        BeginAttempt takeover = beginProgressAttempt(session.getId(), "matrix-crash-key-b", "d".repeat(64));
        assertThat(takeover.error()).as(context("takeover-begin", "matrix-crash-key-b", session.getId(), 1)).isNull();
        String canonicalStory = provider.nextStory();

        assertThat(provider.calls())
                .as("external provider strict exactly-once is intentionally not guaranteed after lease expiry")
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select attempt_count from game_turn_reservation where session_id = ? and expected_turn = ?",
                Integer.class, session.getId(), 1
        )).as("reservation attempt_count session=%d turn=1", session.getId()).isEqualTo(2);

        assertThatThrownBy(() -> commitTurn(session.getId(), crashed.result(), staleStory))
                .as(context("stale-owner-commit", "matrix-crash-key-a", session.getId(), 1))
                .isInstanceOf(TurnConflictException.class)
                .hasMessageContaining("reservation");

        commitTurn(session.getId(), takeover.result(), canonicalStory);

        assertCanonicalTurn(session.getId(), "matrix-crash-key-b", 2, 2);
        assertThat(provider.calls()).isEqualTo(2);
        assertThat(reservationCount(session.getId(), 1)).isZero();
    }

    private Callable<ProviderAttempt> concurrentAttempt(
            CountDownLatch ready,
            CountDownLatch start,
            Long sessionId,
            String key,
            String fingerprint
    ) {
        return () -> {
            ready.countDown();
            start.await();
            BeginAttempt begin = beginProgressAttempt(sessionId, key, fingerprint);
            if (begin.error() != null) {
                return new ProviderAttempt(key, begin, null, false);
            }
            return new ProviderAttempt(key, begin, provider.nextStory(), true);
        };
    }

    private BeginAttempt beginInitAttempt(String key, String fingerprint) {
        return executeBegin(() -> mutationService.begin(
                OWNER_KEY,
                GameMutationRequestService.INIT,
                key,
                null,
                null,
                fingerprint
        ));
    }

    private BeginAttempt beginProgressAttempt(Long sessionId, String key, String fingerprint) {
        return executeBegin(() -> mutationService.begin(
                OWNER_KEY,
                GameMutationRequestService.PROGRESS,
                key,
                sessionId,
                1,
                fingerprint
        ));
    }

    private BeginAttempt executeBegin(Callable<GameMutationRequestService.BeginResult> begin) {
        return transactionTemplate.execute(status -> {
            try {
                return BeginAttempt.success(begin.call());
            } catch (RuntimeException exception) {
                return BeginAttempt.failure(exception);
            } catch (Exception exception) {
                throw new IllegalStateException("mutation begin 실행에 실패했습니다.", exception);
            }
        });
    }

    private GameSession createOpening() {
        return persistenceService.saveOpening(
                OWNER_KEY,
                "matrix-world",
                "matrix-character",
                "opening-story",
                OPENING_CHOICES,
                null
        );
    }

    private void commitTurn(Long sessionId, GameMutationRequestService.BeginResult mutation, String story) {
        GamePersistenceService.LoadedTurn loaded = persistenceService.loadLatestTurn(OWNER_KEY, sessionId, 1);
        GameState nextState = loaded.gameState().advance("문을 연다", story);
        GameTurnCommit commit = new GameTurnCommit(
                1,
                1,
                "문을 연다",
                loaded.gameState(),
                nextState,
                story,
                NEXT_CHOICES,
                null
        );
        persistenceService.saveNextTurn(
                OWNER_KEY,
                sessionId,
                commit,
                mutation.requestId(),
                "matrix-turn-2",
                mutation.reservationOwner()
        );
    }

    private void assertCanonicalTurn(Long sessionId, String completedKey, int expectedTurn, int expectedLogCount) {
        Integer sessionTurn = jdbcTemplate.queryForObject(
                "select current_turn from game_session where id = ?", Integer.class, sessionId
        );
        Integer logCount = jdbcTemplate.queryForObject(
                "select count(*) from game_log where session_id = ?", Integer.class, sessionId
        );
        Integer latestStateVersion = jdbcTemplate.queryForObject(
                "select max(state_version) from game_log where session_id = ?", Integer.class, sessionId
        );
        String requestStatus = jdbcTemplate.queryForObject(
                "select status from game_mutation_request where owner_key = ? and idempotency_key = ?",
                String.class, OWNER_KEY, completedKey
        );
        Integer requestResultTurn = jdbcTemplate.queryForObject(
                "select result_turn from game_mutation_request where owner_key = ? and idempotency_key = ?",
                Integer.class, OWNER_KEY, completedKey
        );

        assertThat(sessionTurn).as("session=%d current_turn", sessionId).isEqualTo(expectedTurn);
        assertThat(logCount).as("session=%d committed GameLog rows", sessionId).isEqualTo(expectedLogCount);
        assertThat(latestStateVersion).as("session=%d latest GameLog state_version", sessionId).isEqualTo(expectedTurn);
        assertThat(requestStatus).as("request=%s status", completedKey).isEqualTo("COMPLETED");
        assertThat(requestResultTurn).as("request=%s result_turn", completedKey).isEqualTo(expectedTurn);

        GamePersistenceService.LoadedTurn loaded = persistenceService.loadLatestTurn(OWNER_KEY, sessionId, expectedTurn);
        assertThat(loaded.gameState().turnNumber()).as("session=%d snapshot/currentTurn", sessionId).isEqualTo(expectedTurn);
    }

    private int reservationCount(Long sessionId, int expectedTurn) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from game_turn_reservation where session_id = ? and expected_turn = ?",
                Integer.class, sessionId, expectedTurn
        );
        return count == null ? 0 : count;
    }

    private String context(String phase, String key, Long sessionId, int turn) {
        return "phase=%s request=%s session=%d turn=%d".formatted(phase, key, sessionId, turn);
    }

    private record BeginAttempt(GameMutationRequestService.BeginResult result, RuntimeException error) {
        static BeginAttempt success(GameMutationRequestService.BeginResult result) {
            return new BeginAttempt(result, null);
        }

        static BeginAttempt failure(RuntimeException error) {
            return new BeginAttempt(null, error);
        }
    }

    private record ProviderAttempt(String key, BeginAttempt begin, String story, boolean providerCalled) {}

    private static final class FakeNarrativeProvider {
        private final AtomicInteger calls = new AtomicInteger();

        String nextStory() {
            return "provider-story-" + calls.incrementAndGet();
        }

        int calls() {
            return calls.get();
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        MutableClock(Instant initialInstant) {
            this.instant = new AtomicReference<>(initialInstant);
        }

        void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
