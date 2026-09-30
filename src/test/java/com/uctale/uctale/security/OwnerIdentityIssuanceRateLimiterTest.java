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

class OwnerIdentityIssuanceRateLimiterTest {

    @Test
    @DisplayName("같은 IP의 새 owner 발급 횟수는 fixed window 한도에서 차단되고 다음 window에 초기화된다")
    void issuanceLimit_BlocksUntilNextWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-22T00:00:00Z"));
        OwnerIdentityIssuanceRateLimiter limiter = limiter(2, 60, clock);

        limiter.check("1.2.3.4");
        limiter.check("1.2.3.4");

        assertThatThrownBy(() -> limiter.check("1.2.3.4"))
                .isInstanceOfSatisfying(
                        OwnerIdentityIssuanceRateLimitExceededException.class,
                        exception -> assertThat(exception.retryAfterSeconds()).isEqualTo(60)
                );

        clock.advanceSeconds(60);
        assertThatCode(() -> limiter.check("1.2.3.4")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("서로 다른 client IP의 owner 발급 bucket은 독립적이다")
    void issuanceLimit_UsesIndependentIpBuckets() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-22T00:00:00Z"));
        OwnerIdentityIssuanceRateLimiter limiter = limiter(1, 60, clock);

        limiter.check("1.2.3.4");

        assertThatThrownBy(() -> limiter.check("1.2.3.4"))
                .isInstanceOf(OwnerIdentityIssuanceRateLimitExceededException.class);
        assertThatCode(() -> limiter.check("5.6.7.8")).doesNotThrowAnyException();
    }

    private OwnerIdentityIssuanceRateLimiter limiter(int limit, long windowSeconds, Clock clock) {
        return new OwnerIdentityIssuanceRateLimiter(
                new OwnerIdentityIssuanceRateLimitPolicy(limit, windowSeconds),
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
