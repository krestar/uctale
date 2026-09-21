package com.uctale.uctale.application.narrative;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NarrativeExecutionPolicy {

    public static final int MAX_PROVIDER_ATTEMPTS = 3;
    private static final long FIRST_RETRY_BACKOFF_MILLIS = 50L;
    private static final long SECOND_RETRY_BACKOFF_MILLIS = 150L;

    private final long connectTimeoutMillis;
    private final long readTimeoutMillis;
    private final long reservationLeaseSeconds;
    private final long recoveryCooldownSeconds;
    private final long maxInlineExecutionMillis;

    public NarrativeExecutionPolicy(
            @Value("${google.ai.connect-timeout-ms:10000}") long connectTimeoutMillis,
            @Value("${google.ai.read-timeout-ms:40000}") long readTimeoutMillis,
            @Value("${app.game.turn-reservation.lease-seconds:180}") long reservationLeaseSeconds,
            @Value("${app.game.turn-reservation.recovery-cooldown-seconds:30}") long recoveryCooldownSeconds
    ) {
        if (connectTimeoutMillis <= 0 || readTimeoutMillis <= 0) {
            throw new IllegalArgumentException("Narrative provider timeout은 0보다 커야 합니다.");
        }
        if (reservationLeaseSeconds <= 0 || recoveryCooldownSeconds <= 0) {
            throw new IllegalArgumentException("턴 reservation lease와 recovery cooldown은 0보다 커야 합니다.");
        }

        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.reservationLeaseSeconds = reservationLeaseSeconds;
        this.recoveryCooldownSeconds = recoveryCooldownSeconds;

        try {
            long timeoutPerAttempt = Math.addExact(connectTimeoutMillis, readTimeoutMillis);
            long allAttempts = Math.multiplyExact(timeoutPerAttempt, MAX_PROVIDER_ATTEMPTS);
            this.maxInlineExecutionMillis = Math.addExact(
                    allAttempts,
                    Math.addExact(FIRST_RETRY_BACKOFF_MILLIS, SECOND_RETRY_BACKOFF_MILLIS)
            );
            long leaseMillis = Math.multiplyExact(reservationLeaseSeconds, 1_000L);
            if (leaseMillis <= maxInlineExecutionMillis) {
                throw new IllegalArgumentException(
                        "턴 reservation lease는 Narrative provider 최대 처리 시간보다 길어야 합니다. "
                                + "leaseMillis=" + leaseMillis + ", maxInlineExecutionMillis=" + maxInlineExecutionMillis
                );
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Narrative 실행 시간 설정이 허용 범위를 초과했습니다.", exception);
        }
    }

    public static NarrativeExecutionPolicy defaults() {
        return new NarrativeExecutionPolicy(10_000L, 40_000L, 180L, 30L);
    }

    public long connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public long readTimeoutMillis() {
        return readTimeoutMillis;
    }

    public long reservationLeaseSeconds() {
        return reservationLeaseSeconds;
    }

    public long recoveryCooldownSeconds() {
        return recoveryCooldownSeconds;
    }

    public long maxInlineExecutionMillis() {
        return maxInlineExecutionMillis;
    }

    public long retryBackoffMillis(int retryIndex) {
        if (retryIndex <= 0) {
            throw new IllegalArgumentException("retryIndex는 1 이상이어야 합니다.");
        }
        return retryIndex == 1 ? FIRST_RETRY_BACKOFF_MILLIS : SECOND_RETRY_BACKOFF_MILLIS;
    }
}
