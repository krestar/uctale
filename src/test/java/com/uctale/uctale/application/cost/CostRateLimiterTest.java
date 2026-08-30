package com.uctale.uctale.application.cost;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CostRateLimiterTest {

    @Test
    @DisplayName("Narrative 한도를 넘으면 Retry-After와 함께 provider 호출 전에 거부할 수 있다")
    void narrativeLimit_IsDeterministic() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T06:00:00Z"));
        CostRateLimiter limiter = new CostRateLimiter(new CostRateLimitPolicy(2, 5, 60), clock);
        CostRequestContext context = new CostRequestContext("r1", "owner-a", "1.2.3.4", 10L, 2, null);

        limiter.check(CostOperation.NARRATIVE, context);
        limiter.check(CostOperation.NARRATIVE, context);

        assertThatThrownBy(() -> limiter.check(CostOperation.NARRATIVE, context))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        exception -> assertThat(exception.retryAfterSeconds()).isEqualTo(60));

        clock.advanceSeconds(60);
        assertThatCode(() -> limiter.check(CostOperation.NARRATIVE, context)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Narrative와 Image quota는 서로 독립적이다")
    void operationQuotas_AreIndependent() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T06:00:00Z"));
        CostRateLimiter limiter = new CostRateLimiter(new CostRateLimitPolicy(1, 2, 60), clock);
        CostRequestContext context = new CostRequestContext("r1", "owner-a", "1.2.3.4", 10L, 2, null);

        limiter.check(CostOperation.NARRATIVE, context);
        assertThatThrownBy(() -> limiter.check(CostOperation.NARRATIVE, context))
                .isInstanceOf(RateLimitExceededException.class);

        assertThatCode(() -> limiter.check(CostOperation.IMAGE, context)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check(CostOperation.IMAGE, context)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check(CostOperation.IMAGE, context))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("owner뿐 아니라 동일 IP의 비용 요청도 합산한다")
    void ipBucket_IsSharedAcrossOwners() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T06:00:00Z"));
        CostRateLimiter limiter = new CostRateLimiter(new CostRateLimitPolicy(1, 5, 60), clock);

        limiter.check(CostOperation.NARRATIVE,
                new CostRequestContext("r1", "owner-a", "1.2.3.4", 10L, 2, null));

        assertThatThrownBy(() -> limiter.check(CostOperation.NARRATIVE,
                new CostRequestContext("r2", "owner-b", "1.2.3.4", 11L, 2, null)))
                .isInstanceOf(RateLimitExceededException.class);
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
