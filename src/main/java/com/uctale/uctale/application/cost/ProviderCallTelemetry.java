package com.uctale.uctale.application.cost;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.function.Supplier;

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
        long startedAt = clock.millis();
        try {
            T result = invocation.get();
            record(provider, operation, context, retryCount, startedAt, "SUCCESS");
            return result;
        } catch (RuntimeException exception) {
            record(provider, operation, context, retryCount, startedAt, "FAILURE");
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
