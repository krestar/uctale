package com.uctale.uctale.application.narrative;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NarrativeRecoveryExecutorTest {

    @Test
    @DisplayName("정상 응답은 retry 없이 그대로 반환한다")
    void normalResponse_DoesNotRetry() {
        List<Long> backoffs = new ArrayList<>();
        AtomicInteger retryGuards = new AtomicInteger();
        NarrativeRecoveryExecutor executor = new NarrativeRecoveryExecutor(3, backoffs::add);

        NarrativeRecoveryExecutor.Result result = executor.execute(
                () -> validTurn("정상"),
                reason -> { throw new AssertionError("repair는 호출되면 안 됩니다."); },
                retryGuards::incrementAndGet
        );

        assertThat(result.turn().title()).isEqualTo("정상");
        assertThat(result.retryCount()).isZero();
        assertThat(backoffs).isEmpty();
        assertThat(retryGuards).hasValue(0);
    }

    @Test
    @DisplayName("repair 가능한 응답 오류는 reason code를 전달하고 bounded retry 후 성공한다")
    void recoverableResponse_RetriesWithReasonCode() {
        List<Long> backoffs = new ArrayList<>();
        List<String> repairReasons = new ArrayList<>();
        AtomicInteger retryGuards = new AtomicInteger();
        NarrativeRecoveryExecutor executor = new NarrativeRecoveryExecutor(3, backoffs::add);

        NarrativeRecoveryExecutor.Result result = executor.execute(
                () -> { throw new RecoverableNarrativeResponseException("INVALID_CHOICE_ID", "중복 선택지"); },
                reason -> { repairReasons.add(reason); return validTurn("복구"); },
                retryGuards::incrementAndGet
        );

        assertThat(result.turn().title()).isEqualTo("복구");
        assertThat(result.retryCount()).isEqualTo(1);
        assertThat(repairReasons).containsExactly("INVALID_CHOICE_ID");
        assertThat(backoffs).containsExactly(50L);
        assertThat(retryGuards).hasValue(1);
    }

    @Test
    @DisplayName("repair가 계속 실패하면 최대 3 provider attempt 뒤 종료한다")
    void retryExhausted_IsBounded() {
        List<Long> backoffs = new ArrayList<>();
        AtomicInteger providerCalls = new AtomicInteger();
        AtomicInteger retryGuards = new AtomicInteger();
        NarrativeRecoveryExecutor executor = new NarrativeRecoveryExecutor(3, backoffs::add);

        assertThatThrownBy(() -> executor.execute(
                () -> fail(providerCalls),
                reason -> fail(providerCalls),
                retryGuards::incrementAndGet
        )).isInstanceOfSatisfying(NarrativeRecoveryExhaustedException.class, exception -> {
            assertThat(exception.retryCount()).isEqualTo(2);
            assertThat(exception.reasonCode()).isEqualTo("MALFORMED_JSON");
        });

        assertThat(providerCalls).hasValue(3);
        assertThat(retryGuards).hasValue(2);
        assertThat(backoffs).containsExactly(50L, 150L);
    }

    @Test
    @DisplayName("retry 직전 guard 실패는 새 provider attempt로 계산하지 않는다")
    void retryGuardFailure_DoesNotCountBlockedAttempt() {
        NarrativeRecoveryExecutor executor = new NarrativeRecoveryExecutor(3, ignored -> {});
        AtomicInteger providerCalls = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute(
                () -> fail(providerCalls),
                reason -> { providerCalls.incrementAndGet(); return validTurn("unexpected"); },
                () -> { throw new IllegalStateException("stale owner"); }
        )).isInstanceOfSatisfying(NarrativeRecoveryInterruptedException.class, exception -> {
            assertThat(exception.retryCount()).isZero();
            assertThat(exception.originalCause()).hasMessage("stale owner");
        });

        assertThat(providerCalls).hasValue(1);
    }

    @Test
    @DisplayName("repair attempt에서 transport failure가 나면 실제 호출 횟수를 보존한다")
    void hardFailureDuringRepair_PreservesAttemptCount() {
        NarrativeRecoveryExecutor executor = new NarrativeRecoveryExecutor(3, ignored -> {});
        Queue<RuntimeException> failures = new ArrayDeque<>();
        failures.add(new RecoverableNarrativeResponseException("INVALID_STORY", "invalid"));
        failures.add(new IllegalStateException("provider down"));
        AtomicInteger providerCalls = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute(
                () -> { providerCalls.incrementAndGet(); throw failures.remove(); },
                reason -> { providerCalls.incrementAndGet(); throw failures.remove(); },
                () -> {}
        )).isInstanceOfSatisfying(NarrativeRecoveryInterruptedException.class, exception -> {
            assertThat(exception.retryCount()).isEqualTo(1);
            assertThat(exception.originalCause()).hasMessage("provider down");
        });

        assertThat(providerCalls).hasValue(2);
    }

    private NarrativeTurn fail(AtomicInteger providerCalls) {
        providerCalls.incrementAndGet();
        throw new RecoverableNarrativeResponseException("MALFORMED_JSON", "invalid");
    }

    private NarrativeTurn validTurn(String title) {
        return new NarrativeTurn(title, "본문", List.of(new NarrativeTurn.Choice(1, "진행한다")), new NarrativeTurn.VisualAssets("", List.of(), List.of()));
    }
}
