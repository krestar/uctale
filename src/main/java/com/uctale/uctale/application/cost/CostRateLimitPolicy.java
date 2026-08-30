package com.uctale.uctale.application.cost;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CostRateLimitPolicy {

    private final int narrativeLimit;
    private final int imageLimit;
    private final long windowSeconds;

    public CostRateLimitPolicy(
            @Value("${game.cost.rate-limit.narrative-limit:12}") int narrativeLimit,
            @Value("${game.cost.rate-limit.image-limit:8}") int imageLimit,
            @Value("${game.cost.rate-limit.window-seconds:60}") long windowSeconds
    ) {
        if (narrativeLimit <= 0 || imageLimit <= 0 || windowSeconds <= 0) {
            throw new IllegalArgumentException("비용 API rate limit 설정은 1 이상이어야 합니다.");
        }
        this.narrativeLimit = narrativeLimit;
        this.imageLimit = imageLimit;
        this.windowSeconds = windowSeconds;
    }

    public int limitFor(CostOperation operation) {
        return operation == CostOperation.IMAGE ? imageLimit : narrativeLimit;
    }

    public long windowSeconds() {
        return windowSeconds;
    }
}
