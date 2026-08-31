package com.uctale.uctale.application.cost;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoggingProviderCallEventSinkTest {

    @Test
    void retry는최초호출을포함한실제Attempt수로집계한다() {
        ProviderUsageStore store = mock(ProviderUsageStore.class);
        ProviderBudgetGuard guard = mock(ProviderBudgetGuard.class);
        ProviderBudgetPolicy policy = new ProviderBudgetPolicy(10, 20, 100, 200, 1, 1, "ALERT_ONLY");
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);
        when(guard.currentUsage()).thenReturn(new ProviderBudgetGuard.Usage(0, 0));
        LoggingProviderCallEventSink sink = new LoggingProviderCallEventSink(store, policy, guard, clock);
        ProviderCallEvent event = new ProviderCallEvent(
                "pollinations", "flux", "image_generation", 1L, 2, "request-1", null, 42, "FAILURE", 2
        );

        sink.record(event);

        verify(store).record(eq(clock.instant()), eq(event), eq(3L));
    }
}
