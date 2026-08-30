package com.uctale.uctale.application.cost;

public record ProviderCallEvent(
        String provider,
        String operation,
        Long sessionId,
        Integer turn,
        String requestId,
        String idempotencyKey,
        long latencyMs,
        String outcome,
        int retryCount
) {}
