package com.uctale.uctale.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessAuthenticationRateLimiterTest {

    @Test
    @DisplayName("같은 IP가 실패 한도에 도달하면 다음 인증 시도부터 Retry-After와 함께 차단된다")
    void failureLimit_BlocksNextAttempt() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T12:00:00Z"));
        AccessAuthenticationRateLimiter limiter = limiter(2, 60, clock);

        assertThatThrownBy(() -> limiter.authenticate("1.2.3.4", this::invalidCredentials))
                .isInstanceOf(AccessSessionException.class);
        assertThatThrownBy(() -> limiter.authenticate("1.2.3.4", this::invalidCredentials))
                .isInstanceOf(AccessSessionException.class);

        assertThatThrownBy(() -> limiter.authenticate("1.2.3.4", () -> "ok"))
                .isInstanceOfSatisfying(AccessAuthenticationRateLimitExceededException.class,
                        exception -> assertThat(exception.retryAfterSeconds()).isEqualTo(60));

        clock.advanceSeconds(60);
        assertThatCode(() -> limiter.authenticate("1.2.3.4", () -> "ok")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("성공 인증은 해당 IP의 연속 실패 기록을 초기화한다")
    void success_ResetsFailureCounter() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T12:00:00Z"));
        AccessAuthenticationRateLimiter limiter = limiter(2, 60, clock);

        assertThatThrownBy(() -> limiter.authenticate("1.2.3.4", this::invalidCredentials))
                .isInstanceOf(AccessSessionException.class);
        assertThat(limiter.authenticate("1.2.3.4", () -> "ok")).isEqualTo("ok");
        assertThatThrownBy(() -> limiter.authenticate("1.2.3.4", this::invalidCredentials))
                .isInstanceOf(AccessSessionException.class);
        assertThatCode(() -> limiter.authenticate("1.2.3.4", () -> "ok")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("서로 다른 IP의 인증 실패 bucket은 독립적이다")
    void ipBuckets_AreIndependent() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T12:00:00Z"));
        AccessAuthenticationRateLimiter limiter = limiter(1, 60, clock);

        assertThatThrownBy(() -> limiter.authenticate("1.2.3.4", this::invalidCredentials))
                .isInstanceOf(AccessSessionException.class);

        assertThatThrownBy(() -> limiter.authenticate("1.2.3.4", () -> "ok"))
                .isInstanceOf(AccessAuthenticationRateLimitExceededException.class);
        assertThat(limiter.authenticate("5.6.7.8", () -> "ok")).isEqualTo("ok");
    }

    @Test
    @DisplayName("인증 판정과 실패 기록은 하나의 원자적 경계에서 처리된다")
    void authenticationAndFailureCounting_ShareAtomicBoundary() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T12:00:00Z"));
        AccessAuthenticationRateLimiter limiter = limiter(1, 60, clock);

        assertThatThrownBy(() -> limiter.authenticate("1.2.3.4", this::invalidCredentials))
                .isInstanceOf(AccessSessionException.class);
        assertThatThrownBy(() -> limiter.authenticate("1.2.3.4", this::invalidCredentials))
                .isInstanceOf(AccessAuthenticationRateLimitExceededException.class);
    }

    private String invalidCredentials() {
        throw new AccessSessionException("INVALID_CREDENTIALS", "비밀번호가 올바르지 않습니다.");
    }

    private AccessAuthenticationRateLimiter limiter(int failureLimit, long windowSeconds, Clock clock) {
        return new AccessAuthenticationRateLimiter(
                new AccessAuthenticationRateLimitPolicy(failureLimit, windowSeconds),
                clock
        );
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
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
            return instant;
        }
    }
}
