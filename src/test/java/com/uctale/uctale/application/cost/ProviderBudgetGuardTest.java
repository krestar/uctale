package com.uctale.uctale.application.cost;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProviderBudgetGuardTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void alertOnly는Critical초과여도호출을차단하지않는다() {
        ProviderUsageStore store = mock(ProviderUsageStore.class);
        when(store.totalUnits(any(), any())).thenReturn(999L);
        ProviderBudgetPolicy policy = new ProviderBudgetPolicy(10, 20, 100, 200, 1, 1, "ALERT_ONLY");
        ProviderBudgetGuard guard = new ProviderBudgetGuard(store, policy, clock);

        assertThatNoException().isThrownBy(() -> guard.checkBeforeCall("progress"));
    }

    @Test
    void failClosed는다음호출이DailyCritical을넘으면차단한다() {
        ProviderUsageStore store = mock(ProviderUsageStore.class);
        when(store.totalUnits(any(), any())).thenReturn(20L, 20L);
        ProviderBudgetPolicy policy = new ProviderBudgetPolicy(10, 20, 100, 200, 1, 1, "FAIL_CLOSED");
        ProviderBudgetGuard guard = new ProviderBudgetGuard(store, policy, clock);

        assertThatThrownBy(() -> guard.checkBeforeCall("progress"))
                .isInstanceOf(ProviderBudgetExceededException.class);
    }

    @Test
    void failClosed는현재값이Critical미만이면호출을허용한다() {
        ProviderUsageStore store = mock(ProviderUsageStore.class);
        when(store.totalUnits(any(), any())).thenReturn(18L, 50L);
        ProviderBudgetPolicy policy = new ProviderBudgetPolicy(10, 20, 100, 200, 1, 1, "FAIL_CLOSED");
        ProviderBudgetGuard guard = new ProviderBudgetGuard(store, policy, clock);

        assertThatNoException().isThrownBy(() -> guard.checkBeforeCall("progress"));
    }
}
