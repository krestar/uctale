package com.uctale.uctale.application.cost;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderBudgetPolicyTest {

    @Test
    void operation별AttemptUnit과CriticalMode를고정한다() {
        ProviderBudgetPolicy policy = new ProviderBudgetPolicy(10, 20, 100, 200, 2, 5, "fail_closed");

        assertThat(policy.unitsPerAttempt("opening")).isEqualTo(2);
        assertThat(policy.unitsPerAttempt("image_generation")).isEqualTo(5);
        assertThat(policy.criticalMode()).isEqualTo(ProviderBudgetPolicy.CriticalMode.FAIL_CLOSED);
    }

    @Test
    void warning이Critical보다크면거부한다() {
        assertThatThrownBy(() -> new ProviderBudgetPolicy(21, 20, 100, 200, 1, 1, "ALERT_ONLY"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
