package com.uctale.uctale.application.narrative;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NarrativeExecutionPolicyTest {

    @Test
    @DisplayName("기본 lease는 최대 3회 provider timeout과 recovery backoff보다 길다")
    void defaults_KeepLeaseBeyondWorstCaseInlineExecution() {
        NarrativeExecutionPolicy policy = NarrativeExecutionPolicy.defaults();

        assertThat(policy.reservationLeaseSeconds() * 1_000L)
                .isGreaterThan(policy.maxInlineExecutionMillis());
    }

    @Test
    @DisplayName("provider 최대 처리 시간 이하의 lease 설정은 startup 정책에서 거부한다")
    void leaseNotLongEnough_IsRejected() {
        assertThatThrownBy(() -> new NarrativeExecutionPolicy(10_000, 40_000, 150, 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최대 처리 시간");
    }
}
