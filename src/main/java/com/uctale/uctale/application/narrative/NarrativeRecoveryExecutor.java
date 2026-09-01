package com.uctale.uctale.application.narrative;

import java.util.function.Function;
import java.util.function.Supplier;

public final class NarrativeRecoveryExecutor {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long[] DEFAULT_BACKOFF_MILLIS = {50L, 150L};

    private final int maxAttempts;
    private final Sleeper sleeper;

    public NarrativeRecoveryExecutor(int maxAttempts, Sleeper sleeper) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Narrative provider attempt 상한은 1 이상이어야 합니다.");
        }
        this.maxAttempts = maxAttempts;
        this.sleeper = sleeper == null ? ignored -> {} : sleeper;
    }

    public static NarrativeRecoveryExecutor production() {
        return new NarrativeRecoveryExecutor(DEFAULT_MAX_ATTEMPTS, millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Narrative recovery backoff가 중단되었습니다.", exception);
            }
        });
    }

    public Result execute(
            Supplier<NarrativeTurn> initialAttempt,
            Function<String, NarrativeTurn> repairAttempt,
            Runnable beforeRetryProviderAttempt
    ) {
        if (initialAttempt == null || repairAttempt == null || beforeRetryProviderAttempt == null) {
            throw new IllegalArgumentException("Narrative recovery callback은 필수입니다.");
        }

        String previousReason = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                try {
                    beforeRetryProviderAttempt.run();
                } catch (RuntimeException exception) {
                    throw new NarrativeRecoveryInterruptedException(Math.max(0, attempt - 2), exception);
                }
            }

            try {
                NarrativeTurn turn = attempt == 1
                        ? initialAttempt.get()
                        : repairAttempt.apply(previousReason);
                return new Result(turn, attempt - 1);
            } catch (RecoverableNarrativeResponseException exception) {
                previousReason = exception.reasonCode();
                if (attempt == maxAttempts) {
                    throw new NarrativeRecoveryExhaustedException(attempt - 1, previousReason, exception);
                }
                sleeper.sleep(backoffMillis(attempt));
            } catch (RuntimeException exception) {
                if (attempt == 1) {
                    throw exception;
                }
                throw new NarrativeRecoveryInterruptedException(attempt - 1, exception);
            }
        }
        throw new IllegalStateException("도달할 수 없는 Narrative recovery 상태입니다.");
    }

    private long backoffMillis(int retryIndex) {
        int index = Math.min(Math.max(retryIndex, 1) - 1, DEFAULT_BACKOFF_MILLIS.length - 1);
        return DEFAULT_BACKOFF_MILLIS[index];
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis);
    }

    public record Result(NarrativeTurn turn, int retryCount) {
        public Result {
            if (turn == null) {
                throw new IllegalArgumentException("Narrative turn은 필수입니다.");
            }
            if (retryCount < 0) {
                throw new IllegalArgumentException("retryCount는 0 이상이어야 합니다.");
            }
        }
    }
}
