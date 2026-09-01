package com.uctale.uctale.application.cost;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ProviderCallTelemetryTest {

    @Test
    @DisplayName("provider 성공 호출은 민감 본문 없이 식별자와 idempotency key, latency를 기록한다")
    void success_IsRecorded() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T06:00:00Z"));
        List<ProviderCallEvent> events = new ArrayList<>();
        ProviderCallTelemetry telemetry = new ProviderCallTelemetry(clock, events::add);
        CostRequestContext context = new CostRequestContext(
                "request-1", "owner-secret", "1.2.3.4", 42L, 3, "mutation-key-123"
        );

        String result = telemetry.observe("gemini", "progress", context, 0, () -> {
            clock.advanceMillis(37);
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.provider()).isEqualTo("gemini");
            assertThat(event.operation()).isEqualTo("progress");
            assertThat(event.sessionId()).isEqualTo(42L);
            assertThat(event.turn()).isEqualTo(3);
            assertThat(event.requestId()).isEqualTo("request-1");
            assertThat(event.idempotencyKey()).isEqualTo("mutation-key-123");
            assertThat(event.latencyMs()).isEqualTo(37);
            assertThat(event.outcome()).isEqualTo("SUCCESS");
            assertThat(event.retryCount()).isZero();
        });
    }

    @Test
    @DisplayName("Gemini telemetry는 application 설정의 Narrative model ID를 기록한다")
    void geminiModel_ComesFromApplicationConfiguration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T06:00:00Z"));
        List<ProviderCallEvent> events = new ArrayList<>();
        ProviderCallTelemetry telemetry = new ProviderCallTelemetry(
                clock, events::add, null, "gemini-3.7-flash", "flux"
        );

        telemetry.observe(
                "gemini",
                "opening",
                CostRequestContext.internal("owner-a", null, 1),
                0,
                () -> "ok"
        );

        assertThat(events).singleElement().satisfies(event ->
                assertThat(event.model()).isEqualTo("gemini-3.7-flash")
        );
    }

    @Test
    @DisplayName("provider 예외도 실패 event를 남기고 원래 예외를 전달한다")
    void failure_IsRecorded() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T06:00:00Z"));
        List<ProviderCallEvent> events = new ArrayList<>();
        ProviderCallTelemetry telemetry = new ProviderCallTelemetry(clock, events::add);
        CostRequestContext context = CostRequestContext.internal("owner-a", 42L, 2);

        assertThatThrownBy(() -> telemetry.observe("pollinations", "image_generation", context, 1, () -> {
            clock.advanceMillis(12);
            throw new IllegalStateException("provider down");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.outcome()).isEqualTo("FAILURE");
            assertThat(event.retryCount()).isEqualTo(1);
            assertThat(event.latencyMs()).isEqualTo(12);
        });
    }

    @Test
    @DisplayName("호출 결과에서 실제 retry 횟수를 추출해 event에 기록할 수 있다")
    void retryCount_CanBeExtractedFromResult() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T06:00:00Z"));
        List<ProviderCallEvent> events = new ArrayList<>();
        ProviderCallTelemetry telemetry = new ProviderCallTelemetry(clock, events::add);
        CostRequestContext context = CostRequestContext.internal("owner-a", 42L, 2);

        RetryAwareResult result = telemetry.observe(
                "pollinations",
                "image_generation",
                context,
                () -> new RetryAwareResult("ok", 2),
                RetryAwareResult::retryCount,
                ignored -> 0
        );

        assertThat(result.value()).isEqualTo("ok");
        assertThat(events).singleElement().satisfies(event -> assertThat(event.retryCount()).isEqualTo(2));
    }

    @Test
    @DisplayName("budget guard가 provider를 차단하면 호출 직전 훅도 실행하지 않는다")
    void budgetGuardRejection_DoesNotRunBeforeInvocationHook() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T06:00:00Z"));
        ProviderUsageStore usageStore = mock(ProviderUsageStore.class);
        given(usageStore.totalUnits(any(), any())).willReturn(10L);
        ProviderBudgetPolicy policy = new ProviderBudgetPolicy(1, 10, 1, 10, 1, 1, "FAIL_CLOSED");
        ProviderBudgetGuard guard = new ProviderBudgetGuard(usageStore, policy, clock);
        ProviderCallTelemetry telemetry = new ProviderCallTelemetry(clock, event -> {}, guard, "flux");
        AtomicBoolean beforeInvocation = new AtomicBoolean(false);
        AtomicBoolean invocation = new AtomicBoolean(false);

        assertThatThrownBy(() -> telemetry.observe(
                "gemini",
                "progress",
                CostRequestContext.internal("owner-a", 42L, 2),
                0,
                () -> beforeInvocation.set(true),
                () -> {
                    invocation.set(true);
                    return "unexpected";
                }
        )).isInstanceOf(ProviderBudgetExceededException.class);

        assertThat(beforeInvocation).isFalse();
        assertThat(invocation).isFalse();
    }

    private record RetryAwareResult(String value, int retryCount) {}

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceMillis(long millis) {
            instant = instant.plusMillis(millis);
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
