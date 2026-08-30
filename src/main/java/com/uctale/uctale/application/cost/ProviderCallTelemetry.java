package com.uctale.uctale.application.cost;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

@Component
public class ProviderCallTelemetry {

    private final Clock clock;
    private final ProviderCallEventSink sink;

    public ProviderCallTelemetry(Clock clock, ProviderCallEventSink sink) {
        this.clock = clock;
        this.sink = sink;
    }

    public <T> T observe(
            String provider,
            String operation,
            CostRequestContext context,
            int retryCount,
            Supplier<T> invocation
    ) {
        return observe(
                provider,
                operation,
                context,
                invocation,
                ignored -> retryCount,
                ignored -> retryCount
        );
    }

    public <T> T observe(
            String provider,
            String operation,
            CostRequestContext context,
            Supplier<T> invocation,
            ToIntFunction<T> successRetryCount,
            ToIntFunction<RuntimeException> failureRetryCount
    ) {
        long startedAt = clock.millis();
        try {
            T result = invocation.get();
            record(provider, operation, context, Math.max(0, successRetryCount.applyAsInt(result)), startedAt, "SUCCESS");
            return result;
        } catch (RuntimeException exception) {
            record(provider, operation, context, Math.max(0, failureRetryCount.applyAsInt(exception)), startedAt, "FAILURE");
            throw exception;
        }
    }

    private void record(
            String provider,
            String operation,
            CostRequestContext context,
            int retryCount,
            long startedAt,
            String outcome
    ) {
        sink.record(new ProviderCallEvent(
                provider,
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
