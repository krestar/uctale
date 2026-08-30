package com.uctale.uctale.application.cost;

import java.util.UUID;

public record CostRequestContext(
        String requestId,
        String ownerKey,
        String clientIp,
        Long sessionId,
        Integer turn,
        String idempotencyKey
) {
    public static CostRequestContext create(
            String ownerKey,
            String clientIp,
            Long sessionId,
            Integer turn,
            String idempotencyKey
    ) {
        return new CostRequestContext(UUID.randomUUID().toString(), ownerKey, clientIp, sessionId, turn, idempotencyKey);
    }

    public static CostRequestContext create(String ownerKey, String clientIp, Long sessionId, Integer turn) {
        return create(ownerKey, clientIp, sessionId, turn, null);
    }

    public static CostRequestContext internal(String ownerKey, Long sessionId, Integer turn) {
        return create(ownerKey, "internal", sessionId, turn, "internal-" + UUID.randomUUID());
    }
}
