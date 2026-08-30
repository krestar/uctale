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
        AccessAuthenticationRateLimiter limiter = new AccessAuthenticationRateLimiter(
                new AccessAuthenticationRateLimitPolicy(2, 60),
                clock
        );

        limiter.recordFailure("1.2.3.4");
        assertThatCode(() -> limiter.check("1.2.3.4")).doesNotThrowAnyException();
        limiter.recordFailure("1.2.3.4");

        assertThatThrownBy(() -> limiter.check("1.2.3.4"))
                .isInstanceOfSatisfying(AccessAuthenticationRateLimitExceededException.class,
                        exception -> assertThat(exception.retryAfterSeconds()).isEqualTo(60));

        clock.advanceSeconds(60);
        assertThatCode(() -> limiter.check("1.2.3.4")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("성공 인증은 해당 IP의 연속 실패 기록을 초기화한다")
    void success_ResetsFailureCounter() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T12:00:00Z"));
        AccessAuthenticationRateLimiter limiter = new AccessAuthenticationRateLimiter(
                new AccessAuthenticationRateLimitPolicy(2, 60),
                clock
        );

        limiter.recordFailure("1.2.3.4");
        limiter.recordFailure("1.2.3.4");
        limiter.recordSuccess("1.2.3.4");

        assertThatCode(() -> limiter.check("1.2.3.4")).doesNotThrowAnyException();
        limiter.recordFailure("1.2.3.4");
        assertThatCode(() -> limiter.check("1.2.3.4")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("서로 다른 IP의 인증 실패 bucket은 독립적이다")
    void ipBuckets_AreIndependent() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T12:00:00Z"));
        AccessAuthenticationRateLimiter limiter = new AccessAuthenticationRateLimiter(
                new AccessAuthenticationRateLimitPolicy(1, 60),
                clock
        );

        limiter.recordFailure("1.2.3.4");

        assertThatThrownBy(() -> limiter.check("1.2.3.4"))
                .isInstanceOf(AccessAuthenticationRateLimitExceededException.class);
        assertThatCode(() -> limiter.check("5.6.7.8")).doesNotThrowAnyException();
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
