package com.uctale.uctale.security;

public class AccessAuthenticationRateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public AccessAuthenticationRateLimitExceededException(long retryAfterSeconds) {
        super("접근 비밀번호 인증 시도 한도를 초과했습니다.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
