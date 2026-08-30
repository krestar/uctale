package com.uctale.uctale.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

@Component
public class AccessAuthenticationRateLimiter {

    private final AccessAuthenticationRateLimitPolicy policy;
    private final Clock clock;
    private final Map<String, Counter> counters = new HashMap<>();

    public AccessAuthenticationRateLimiter(AccessAuthenticationRateLimitPolicy policy, Clock clock) {
        this.policy = policy;
        this.clock = clock;
    }

    public synchronized void check(String clientIp) {
        long now = clock.instant().getEpochSecond();
        long window = now / policy.windowSeconds();
        String key = normalize(clientIp);
        Counter counter = counters.get(key);

        if (counter != null && counter.window == window && counter.failures >= policy.failureLimit()) {
            long retryAfter = Math.max(1, ((window + 1) * policy.windowSeconds()) - now);
            throw new AccessAuthenticationRateLimitExceededException(retryAfter);
        }
    }

    public synchronized void recordFailure(String clientIp) {
        long now = clock.instant().getEpochSecond();
        long window = now / policy.windowSeconds();
        String key = normalize(clientIp);
        Counter counter = counters.get(key);

        if (counter == null || counter.window != window) {
            counters.put(key, new Counter(window, 1));
        } else {
            counter.failures++;
        }

        if (counters.size() > 10_000) {
            counters.entrySet().removeIf(entry -> entry.getValue().window < window - 1);
        }
    }

    public synchronized void recordSuccess(String clientIp) {
        counters.remove(normalize(clientIp));
    }

    private String normalize(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
    }

    private static final class Counter {
        private final long window;
        private int failures;

        private Counter(long window, int failures) {
            this.window = window;
            this.failures = failures;
        }
    }
}
