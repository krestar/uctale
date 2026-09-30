package com.uctale.uctale.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OwnerIdentityIssuanceRateLimitPolicy {

    private final int issuanceLimit;
    private final long windowSeconds;

    public OwnerIdentityIssuanceRateLimitPolicy(
            @Value("${game.owner.issuance-rate-limit.limit:5}") int issuanceLimit,
            @Value("${game.owner.issuance-rate-limit.window-seconds:3600}") long windowSeconds
    ) {
        if (issuanceLimit <= 0 || windowSeconds <= 0) {
            throw new IllegalArgumentException("owner identity 발급 rate limit 설정은 1 이상이어야 합니다.");
        }
        this.issuanceLimit = issuanceLimit;
        this.windowSeconds = windowSeconds;
    }

    public int issuanceLimit() {
        return issuanceLimit;
    }

    public long windowSeconds() {
        return windowSeconds;
    }
}
