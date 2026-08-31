package com.uctale.uctale.application.cost;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

@Component
public class ProviderCallTelemetry {

    private final Clock clock;
    private final ProviderCallEventSink sink;
    private final ProviderBudgetGuard budgetGuard;

    public ProviderCallTelemetry(Clock clock, ProviderCallEventSink sink, ProviderBudgetGuard budgetGuard) {
        this.clock = clock;
        this.sink = sink;
        this.budgetGuard = budgetGuard;
    }

    public <T> T observe(
            String provider,
            String model,
            String operation,
            CostRequestContext context,
            int retryCount,
            Supplier<T> invocation
    ) {
        return observe(
                provider,
                model,
                operation,
                context,
                invocation,
                ignored -> retryCount,
                ignored -> retryCount
        );
    }

    public <T> T observe(
            String provider,
            String model,
            String operation,
            CostRequestContext context,
            Supplier<T> invocation,
            ToIntFunction<T> successRetryCount,
            ToIntFunction<RuntimeException> failureRetryCount
    ) {
        budgetGuard.checkBeforeCall(operation);
        long startedAt = clock.millis();
        try {
            T result = invocation.get();
            record(provider, model, operation, context, Math.max(0, successRetryCount.applyAsInt(result)), startedAt, "SUCCESS");
            return result;
        } catch (RuntimeException exception) {
            record(provider, model, operation, context, Math.max(0, failureRetryCount.applyAsInt(exception)), startedAt, "FAILURE");
            throw exception;
        }
    }

    private void record(
            String provider,
            String model,
            String operation,
            CostRequestContext context,
            int retryCount,
            long startedAt,
            String outcome
    ) {
        sink.record(new ProviderCallEvent(
                provider,
                model,
                operation,
                context.sessionId(),
                context.turn(),
                context.requestId(),
                context.idempotencyKey(),
                Math.max(0, clock.millis() - startedAt),
                outcome,
                retryCount
        ));
    }
}
