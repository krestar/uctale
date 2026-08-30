package com.uctale.uctale.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AccessAuthenticationRateLimitPolicy {

    private final int failureLimit;
    private final long windowSeconds;

    public AccessAuthenticationRateLimitPolicy(
            @Value("${game.access.rate-limit.failure-limit:5}") int failureLimit,
            @Value("${game.access.rate-limit.window-seconds:300}") long windowSeconds
    ) {
        if (failureLimit <= 0 || windowSeconds <= 0) {
            throw new IllegalArgumentException("접근 비밀번호 rate limit 설정은 1 이상이어야 합니다.");
        }
        this.failureLimit = failureLimit;
        this.windowSeconds = windowSeconds;
    }

    public int failureLimit() {
        return failureLimit;
    }

    public long windowSeconds() {
        return windowSeconds;
    }
}
