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
    void sharedBeta운영정책은고정PolicyId로식별한다() {
        ProviderBudgetPolicy policy = new ProviderBudgetPolicy(
                500, 750, 10_000, 15_000, 1, 1, "ALERT_ONLY"
        );

        assertThat(policy.effectivePolicyId()).isEqualTo(ProviderBudgetPolicy.SHARED_BETA_V1_POLICY_ID);
    }

    @Test
    void 임계값이나Mode가다르면Custom정책으로식별한다() {
        ProviderBudgetPolicy thresholdOverride = new ProviderBudgetPolicy(
                501, 750, 10_000, 15_000, 1, 1, "ALERT_ONLY"
        );
        ProviderBudgetPolicy modeOverride = new ProviderBudgetPolicy(
                500, 750, 10_000, 15_000, 1, 1, "FAIL_CLOSED"
        );
        ProviderBudgetPolicy attemptOverride = new ProviderBudgetPolicy(
                500, 750, 10_000, 15_000, 2, 1, "ALERT_ONLY"
        );

        assertThat(thresholdOverride.effectivePolicyId()).isEqualTo("custom");
        assertThat(modeOverride.effectivePolicyId()).isEqualTo("custom");
        assertThat(attemptOverride.effectivePolicyId()).isEqualTo("custom");
    }

    @Test
    void warning이Critical보다크면거부한다() {
        assertThatThrownBy(() -> new ProviderBudgetPolicy(21, 20, 100, 200, 1, 1, "ALERT_ONLY"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
