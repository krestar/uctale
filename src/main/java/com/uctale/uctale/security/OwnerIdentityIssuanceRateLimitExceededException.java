package com.uctale.uctale.security;

public class OwnerIdentityIssuanceRateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public OwnerIdentityIssuanceRateLimitExceededException(long retryAfterSeconds) {
        super("새 owner identity 발급 한도를 초과했습니다.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
