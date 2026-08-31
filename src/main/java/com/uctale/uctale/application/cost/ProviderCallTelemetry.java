package com.uctale.uctale.application.cost;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

@Component
public class ProviderCallTelemetry {

    private final Clock clock;
    private final ProviderCallEventSink sink;
    private final ProviderBudgetGuard budgetGuard;
    private final String imageModel;

    @Autowired
    public ProviderCallTelemetry(
            Clock clock,
            ProviderCallEventSink sink,
            ProviderBudgetGuard budgetGuard,
            @Value("${game.image.model:flux}") String imageModel
    ) {
        this.clock = clock;
        this.sink = sink;
        this.budgetGuard = budgetGuard;
        this.imageModel = imageModel;
    }

    public ProviderCallTelemetry(Clock clock, ProviderCallEventSink sink) {
        this.clock = clock;
        this.sink = sink;
        this.budgetGuard = null;
        this.imageModel = "configured";
    }

    public <T> T observe(
            String provider,
            String operation,
            CostRequestContext context,
            int retryCount,
            Supplier<T> invocation
    ) {
        return observe(provider, defaultModel(provider), operation, context, retryCount, () -> {}, invocation);
    }

    public <T> T observe(
            String provider,
            String operation,
            CostRequestContext context,
            int retryCount,
            Runnable beforeInvocation,
            Supplier<T> invocation
    ) {
        return observe(provider, defaultModel(provider), operation, context, retryCount, beforeInvocation, invocation);
    }

    public <T> T observe(
            String provider,
            String operation,
            CostRequestContext context,
            Supplier<T> invocation,
            ToIntFunction<T> successRetryCount,
            ToIntFunction<RuntimeException> failureRetryCount
    ) {
        return observe(
                provider,
                defaultModel(provider),
                operation,
                context,
                () -> {},
                invocation,
                successRetryCount,
                failureRetryCount
        );
    }

    public <T> T observe(
            String provider,
            String model,
            String operation,
            CostRequestContext context,
            int retryCount,
            Supplier<T> invocation
    ) {
        return observe(provider, model, operation, context, retryCount, () -> {}, invocation);
    }

    public <T> T observe(
            String provider,
            String model,
            String operation,
            CostRequestContext context,
            int retryCount,
            Runnable beforeInvocation,
            Supplier<T> invocation
    ) {
        return observe(
                provider,
                model,
                operation,
                context,
                beforeInvocation,
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
        return observe(
                provider,
                model,
                operation,
                context,
                () -> {},
                invocation,
                successRetryCount,
                failureRetryCount
        );
    }

    public <T> T observe(
            String provider,
            String model,
            String operation,
            CostRequestContext context,
            Runnable beforeInvocation,
            Supplier<T> invocation,
            ToIntFunction<T> successRetryCount,
            ToIntFunction<RuntimeException> failureRetryCount
    ) {
        if (budgetGuard != null) {
            budgetGuard.checkBeforeCall(operation);
        }
        beforeInvocation.run();
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

    private String defaultModel(String provider) {
        return "gemini".equals(provider) ? "gemini-2.5-flash" : imageModel;
    }
}
