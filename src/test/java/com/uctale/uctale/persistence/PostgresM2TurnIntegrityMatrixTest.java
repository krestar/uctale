package com.uctale.uctale.persistence;

import com.uctale.uctale.application.cost.CostRateLimiter;
import com.uctale.uctale.application.cost.CostRequestContext;
import com.uctale.uctale.application.cost.ProviderCallTelemetry;
import com.uctale.uctale.application.game.ChoiceCodec;
import com.uctale.uctale.application.game.GameMutationFingerprint;
import com.uctale.uctale.application.game.GameMutationRequestService;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.GameTurnCommit;
import com.uctale.uctale.application.game.IdempotencyConflictException;
import com.uctale.uctale.application.game.ImagePromptComposer;
import com.uctale.uctale.application.game.MutationInProgressException;
import com.uctale.uctale.application.game.SkillCheckDecisionService;
import com.uctale.uctale.application.game.TurnConflictException;
import com.uctale.uctale.application.game.TurnProcessor;
import com.uctale.uctale.application.image.ImageAssetService;
import com.uctale.uctale.application.narrative.NarrativeContext;
import com.uctale.uctale.application.narrative.NarrativeGenerator;
import com.uctale.uctale.application.narrative.NarrativeTurn;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import com.uctale.uctale.dto.GameResponse;
import com.uctale.uctale.repository.GameMutationRequestRepository;
import com.uctale.uctale.service.GameService;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PostgresM2TurnIntegrityMatrixTest extends PostgresIntegrationTestSupport {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final int LEASE_SECONDS = 30;
    private static final long ASYNC_TIMEOUT_SECONDS = 5;

    @Autowired private GamePersistenceService persistenceService;
    @Autowired private GameMutationRequestRepository mutationRequestRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ImageAssetService imageAssetService;
    @Autowired private ChoiceCodec choiceCodec;
    @Autowired private ImagePromptComposer imagePromptComposer;
    @Autowired private CostRateLimiter costRateLimiter;
    @Autowired private ProviderCallTelemetry providerCallTelemetry;
    @Autowired private GameMutationFingerprint mutationFingerprint;

    private MutableClock clock;
    private TransactionalMutationService mutationService;
    private FakeNarrativeGenerator narrativeGenerator;
    private GameService gameService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                truncate table game_turn_reservation, game_mutation_request, image_asset,
                    game_state_snapshot, game_log, game_session restart identity
                """);
        clock = new MutableClock(Instant.parse("2026-08-31T04:00:00Z"));
        mutationService = new TransactionalMutationService(
                mutationRequestRepository, jdbcTemplate, clock, LEASE_SECONDS, transactionManager);
        narrativeGenerator = new FakeNarrativeGenerator();
        gameService = newGameService(persistenceService);
    }

    @Test
    @DisplayName("완료된 init retry는 실제 provider 경로를 재호출하지 않고 canonical opening을 재사용한다")
    void completedInitRetry_ReusesCanonicalOpeningExactlyOnce() {
        String key = "matrix-init-key-001";
        GameInitRequest request = new GameInitRequest("matrix-world", "matrix-character");
        GameResponse first = gameService.initGame(initContext(key), request);
        GameResponse retry = gameService.initGame(initContext(key), request);
        assertThat(retry.sessionId()).isEqualTo(first.sessionId());
        assertThat(retry.turnNumber()).isEqualTo(1);
        assertThat(retry.storyText()).isEqualTo(first.storyText());
        assertThat(narrativeGenerator.openingCalls()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from game_session", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> gameService.initGame(initContext(key),
                new GameInitRequest("different-world", "matrix-character")))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(narrativeGenerator.openingCalls()).isEqualTo(1);
        assertCanonicalTurn(first.sessionId(), key, 1, 1);
    }

    @Test
    @DisplayName("완료된 progress retry는 실제 provider 경로를 재호출하지 않고 canonical turn을 재사용한다")
    void completedProgressRetry_ReusesCanonicalResultExactlyOnce() {
        GameResponse opening = createOpening("matrix-opening-progress");
        String key = "matrix-progress-key-001";
        GameProgressRequest request = new GameProgressRequest(opening.sessionId(), 1, 1);
        GameResponse first = gameService.progressGame(progressContext(key, opening.sessionId()), request);
        GameResponse retry = gameService.progressGame(progressContext(key, opening.sessionId()), request);
        assertThat(retry.sessionId()).isEqualTo(first.sessionId());
        assertThat(retry.turnNumber()).isEqualTo(2);
        assertThat(retry.storyText()).isEqualTo(first.storyText());
        assertThat(narrativeGenerator.progressCalls()).isEqualTo(1);
        assertThatThrownBy(() -> gameService.progressGame(progressContext(key, opening.sessionId()),
                new GameProgressRequest(opening.sessionId(), 2, 1)))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(narrativeGenerator.progressCalls()).isEqualTo(1);
        assertCanonicalTurn(opening.sessionId(), key, 2, 2);
    }

    @RepeatedTest(3)
    @DisplayName("같은 session/turn 최초 동시 요청은 active lease 동안 실제 provider 진입을 하나로 제한한다")
    void concurrentFirstRequests_AllowSingleActiveProviderExecution() throws Exception {
        GameResponse opening = createOpening("matrix-opening-concurrency");
        GameProgressRequest request = new GameProgressRequest(opening.sessionId(), 1, 1);
        narrativeGenerator.blockNextProgress();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch rejected = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ProgressAttempt> first = executor.submit(concurrentProgressAttempt(ready, start, rejected, "matrix-concurrent-key-a", request));
            Future<ProgressAttempt> second = executor.submit(concurrentProgressAttempt(ready, start, rejected, "matrix-concurrent-key-b", request));
            awaitLatch(ready, "동시 요청 준비");
            start.countDown();
            narrativeGenerator.awaitProgressEntry();
            awaitLatch(rejected, "reservation 경쟁 요청 거부");
            assertThat(narrativeGenerator.progressCalls()).isEqualTo(1);
            assertThat(reservationCount(opening.sessionId(), 1)).isEqualTo(1);
            narrativeGenerator.releaseProgress();
            List<ProgressAttempt> attempts = List.of(first.get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    second.get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            List<ProgressAttempt> winners = attempts.stream().filter(ProgressAttempt::succeeded).toList();
            List<ProgressAttempt> losers = attempts.stream().filter(attempt -> !attempt.succeeded()).toList();
            assertThat(winners).hasSize(1);
            assertThat(losers).hasSize(1);
            assertThat(losers.getFirst().error()).isInstanceOf(MutationInProgressException.class);
            assertThat(narrativeGenerator.progressCalls()).isEqualTo(1);
            assertCanonicalTurn(opening.sessionId(), winners.getFirst().key(), 2, 2);
            assertThat(reservationCount(opening.sessionId(), 1)).isZero();
        } finally {
            narrativeGenerator.releaseProgress();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("provider 성공 직후 crash와 lease expiry는 재호출을 허용해도 stale owner를 막고 canonical commit 하나로 수렴한다")
    void expiredLeaseTakeover_AllowsProviderRetryButSingleCanonicalCommit() throws Exception {
        GameResponse opening = createOpening("matrix-opening-crash");
        GameProgressRequest request = new GameProgressRequest(opening.sessionId(), 1, 1);
        String crashedKey = "matrix-crash-key-a";
        GameService crashingService = newGameService(new CrashBeforeCommitPersistence(persistenceService));
        assertThatThrownBy(() -> crashingService.progressGame(progressContext(crashedKey, opening.sessionId()), request))
                .isInstanceOf(SimulatedCrash.class);
        Long crashedRequestId = requestId(crashedKey);
        String staleOwner = reservationOwner(opening.sessionId(), 1);
        assertThat(narrativeGenerator.progressCalls()).isEqualTo(1);
        assertThat(requestStatus(crashedKey)).isEqualTo("PROCESSING");
        assertThat(reservationCount(opening.sessionId(), 1)).isEqualTo(1);
        clock.advance(Duration.ofSeconds(LEASE_SECONDS + 1));
        narrativeGenerator.blockNextProgress();
        String takeoverKey = "matrix-crash-key-b";
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<GameResponse> takeover = executor.submit(() -> gameService.progressGame(
                    progressContext(takeoverKey, opening.sessionId()), request));
            narrativeGenerator.awaitProgressEntry();
            assertThat(narrativeGenerator.progressCalls()).isEqualTo(2);
            assertThat(reservationOwner(opening.sessionId(), 1)).isNotEqualTo(staleOwner);
            assertThat(jdbcTemplate.queryForObject(
                    "select attempt_count from game_turn_reservation where session_id = ? and expected_turn = ?",
                    Integer.class, opening.sessionId(), 1)).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject(
                    "select provider_attempt_count from game_turn_reservation where session_id = ? and expected_turn = ?",
                    Integer.class, opening.sessionId(), 1)).isEqualTo(2);
            assertThatThrownBy(() -> commitWithReservation(opening.sessionId(), crashedRequestId, staleOwner, "stale-owner-story"))
                    .isInstanceOf(TurnConflictException.class).hasMessageContaining("reservation");
            narrativeGenerator.releaseProgress();
            GameResponse recovered = takeover.get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(recovered.turnNumber()).isEqualTo(2);
            assertThat(narrativeGenerator.progressCalls()).isEqualTo(2);
            assertCanonicalTurn(opening.sessionId(), takeoverKey, 2, 2);
            assertThat(reservationCount(opening.sessionId(), 1)).isZero();
        } finally {
            narrativeGenerator.releaseProgress();
            executor.shutdownNow();
        }
    }

    private Callable<ProgressAttempt> concurrentProgressAttempt(CountDownLatch ready, CountDownLatch start,
            CountDownLatch rejected, String key, GameProgressRequest request) {
        return () -> {
            ready.countDown();
            if (!start.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return ProgressAttempt.failure(key, new IllegalStateException("동시 요청 시작 신호를 받지 못했습니다."));
            }
            try {
                return ProgressAttempt.success(key, gameService.progressGame(progressContext(key, request.sessionId()), request));
            } catch (RuntimeException exception) {
                if (exception instanceof MutationInProgressException) rejected.countDown();
                return ProgressAttempt.failure(key, exception);
            }
        };
    }

    private void awaitLatch(CountDownLatch latch, String phase) throws InterruptedException {
        assertThat(latch.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)).as(phase).isTrue();
    }

    private GameResponse createOpening(String key) {
        return gameService.initGame(initContext(key), new GameInitRequest("matrix-world", "matrix-character"));
    }

    private GameService newGameService(GamePersistenceService persistence) {
        SkillCheckDecisionService skillDecisions = new SkillCheckDecisionService(jdbcTemplate, clock);
        TurnProcessor turnProcessor = new TurnProcessor(skillDecisions, (min, max) -> 10);
        return new GameService(narrativeGenerator, imageAssetService, persistence, choiceCodec, turnProcessor,
                imagePromptComposer, costRateLimiter, providerCallTelemetry, mutationFingerprint, mutationService);
    }

    private CostRequestContext initContext(String key) {
        return CostRequestContext.create(OWNER_KEY, "127.0.0.1", null, 1, key);
    }

    private CostRequestContext progressContext(String key, Long sessionId) {
        return CostRequestContext.create(OWNER_KEY, "127.0.0.1", sessionId, 2, key);
    }

    private void commitWithReservation(Long sessionId, Long requestId, String reservationOwner, String story) {
        GamePersistenceService.LoadedTurn loaded = persistenceService.loadLatestTurn(OWNER_KEY, sessionId, 1);
        var nextState = loaded.gameState().advance("문을 연다", story);
        GameTurnCommit commit = new GameTurnCommit(1, 1, "문을 연다", loaded.gameState(), nextState,
                story, loaded.choicesJson(), null);
        persistenceService.saveNextTurn(OWNER_KEY, sessionId, commit, requestId, "stale-owner-title", reservationOwner);
    }

    private void assertCanonicalTurn(Long sessionId, String completedKey, int expectedTurn, int expectedLogCount) {
        Integer sessionTurn = jdbcTemplate.queryForObject("select current_turn from game_session where id = ?", Integer.class, sessionId);
        Integer logCount = jdbcTemplate.queryForObject("select count(*) from game_log where session_id = ?", Integer.class, sessionId);
        Integer latestStateVersion = jdbcTemplate.queryForObject("select max(state_version) from game_log where session_id = ?", Integer.class, sessionId);
        Integer snapshotTurn = persistenceService.loadLatestTurn(OWNER_KEY, sessionId, expectedTurn).gameState().turnNumber();
        Integer requestResultTurn = jdbcTemplate.queryForObject(
                "select result_turn from game_mutation_request where owner_key = ? and idempotency_key = ?",
                Integer.class, OWNER_KEY, completedKey);
        assertThat(sessionTurn).isEqualTo(expectedTurn);
        assertThat(logCount).isEqualTo(expectedLogCount);
        assertThat(latestStateVersion).isEqualTo(expectedTurn);
        assertThat(snapshotTurn).isEqualTo(expectedTurn);
        assertThat(requestStatus(completedKey)).isEqualTo("COMPLETED");
        assertThat(requestResultTurn).isEqualTo(expectedTurn);
    }

    private Long requestId(String key) {
        return jdbcTemplate.queryForObject("select id from game_mutation_request where owner_key = ? and idempotency_key = ?",
                Long.class, OWNER_KEY, key);
    }

    private String requestStatus(String key) {
        return jdbcTemplate.queryForObject("select status from game_mutation_request where owner_key = ? and idempotency_key = ?",
                String.class, OWNER_KEY, key);
    }

    private String reservationOwner(Long sessionId, int expectedTurn) {
        return jdbcTemplate.queryForObject("select lease_owner from game_turn_reservation where session_id = ? and expected_turn = ?",
                String.class, sessionId, expectedTurn);
    }

    private int reservationCount(Long sessionId, int expectedTurn) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from game_turn_reservation where session_id = ? and expected_turn = ?",
                Integer.class, sessionId, expectedTurn);
        return count == null ? 0 : count;
    }

    private record ProgressAttempt(String key, GameResponse response, RuntimeException error) {
        static ProgressAttempt success(String key, GameResponse response) { return new ProgressAttempt(key, response, null); }
        static ProgressAttempt failure(String key, RuntimeException error) { return new ProgressAttempt(key, null, error); }
        boolean succeeded() { return response != null; }
    }

    private static final class FakeNarrativeGenerator implements NarrativeGenerator {
        private final AtomicInteger openingCalls = new AtomicInteger();
        private final AtomicInteger progressCalls = new AtomicInteger();
        private final AtomicReference<CountDownLatch> progressEntered = new AtomicReference<>();
        private final AtomicReference<CountDownLatch> progressRelease = new AtomicReference<>();
        @Override public NarrativeTurn createOpening(String worldSetting, String characterSetting) {
            return turn("opening-title", "opening-story-" + openingCalls.incrementAndGet());
        }
        @Override public NarrativeTurn createNextTurn(NarrativeContext context) {
            int call = progressCalls.incrementAndGet();
            CountDownLatch entered = progressEntered.get();
            CountDownLatch release = progressRelease.get();
            if (entered != null && release != null) {
                entered.countDown();
                try {
                    if (!release.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) throw new IllegalStateException("fake provider timeout");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("fake provider interrupted", exception);
                }
            }
            return turn("progress-title", "progress-story-" + call);
        }
        void blockNextProgress() { progressEntered.set(new CountDownLatch(1)); progressRelease.set(new CountDownLatch(1)); }
        void awaitProgressEntry() throws InterruptedException {
            CountDownLatch entered = progressEntered.get();
            if (entered != null) assertThat(entered.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
        void releaseProgress() {
            CountDownLatch release = progressRelease.getAndSet(null);
            if (release != null) release.countDown();
            progressEntered.set(null);
        }
        int openingCalls() { return openingCalls.get(); }
        int progressCalls() { return progressCalls.get(); }
        private NarrativeTurn turn(String title, String story) {
            return new NarrativeTurn(title, story, List.of(new NarrativeTurn.Choice(1, "문을 연다")),
                    new NarrativeTurn.VisualAssets(null, List.of(), List.of()));
        }
    }

    private static final class TransactionalMutationService extends GameMutationRequestService {
        private final TransactionTemplate transactionTemplate;
        TransactionalMutationService(GameMutationRequestRepository repository, JdbcTemplate jdbcTemplate, Clock clock,
                long leaseSeconds, PlatformTransactionManager transactionManager) {
            super(repository, jdbcTemplate, clock, leaseSeconds);
            this.transactionTemplate = new TransactionTemplate(transactionManager);
        }
        @Override public BeginResult begin(String ownerKey, String operation, String idempotencyKey, Long sessionId,
                Integer expectedTurn, String fingerprint) {
            BeginOutcome outcome = transactionTemplate.execute(status -> {
                try { return BeginOutcome.success(super.begin(ownerKey, operation, idempotencyKey, sessionId, expectedTurn, fingerprint)); }
                catch (MutationInProgressException exception) { return BeginOutcome.blocked(exception); }
            });
            if (outcome == null) throw new IllegalStateException("mutation begin transaction 결과가 없습니다.");
            if (outcome.blocked() != null) throw outcome.blocked();
            return outcome.result();
        }
        @Override public void markProviderAttemptStarted(Long requestId, String reservationOwner) {
            MutationInProgressException blocked = transactionTemplate.execute(status -> {
                try { super.markProviderAttemptStarted(requestId, reservationOwner); return null; }
                catch (MutationInProgressException exception) { return exception; }
            });
            if (blocked != null) throw blocked;
        }
        @Override public void markFailed(Long requestId) {
            transactionTemplate.executeWithoutResult(status -> super.markFailed(requestId, null));
        }
        @Override public void markFailed(Long requestId, String reservationOwner) {
            transactionTemplate.executeWithoutResult(status -> super.markFailed(requestId, reservationOwner));
        }
        private record BeginOutcome(BeginResult result, MutationInProgressException blocked) {
            static BeginOutcome success(BeginResult result) { return new BeginOutcome(result, null); }
            static BeginOutcome blocked(MutationInProgressException exception) { return new BeginOutcome(null, exception); }
        }
    }

    private static final class CrashBeforeCommitPersistence extends GamePersistenceService {
        private final GamePersistenceService delegate;
        CrashBeforeCommitPersistence(GamePersistenceService delegate) {
            super(null, null, null, null, null, null, null);
            this.delegate = delegate;
        }
        @Override public LoadedTurn loadLatestTurn(String ownerKey, Long sessionId, int expectedTurn) {
            return delegate.loadLatestTurn(ownerKey, sessionId, expectedTurn);
        }
        @Override public int saveNextTurn(String ownerKey, Long sessionId, GameTurnCommit commit,
                Long mutationRequestId, String resultTitle, String reservationOwner) {
            throw new SimulatedCrash();
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        MutableClock(Instant initialInstant) { this.instant = new AtomicReference<>(initialInstant); }
        void advance(Duration duration) { instant.updateAndGet(current -> current.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }

    private static final class SimulatedCrash extends Error {
        SimulatedCrash() { super("simulated process crash after provider success"); }
    }
}
