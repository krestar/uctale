package com.uctale.uctale.application.cost;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CostRateLimiter {

    private final CostRateLimitPolicy policy;
    private final Clock clock;
    private final Map<String, Counter> counters = new HashMap<>();

    public CostRateLimiter(CostRateLimitPolicy policy, Clock clock) {
        this.policy = policy;
        this.clock = clock;
    }

    public synchronized void check(CostOperation operation, CostRequestContext context) {
        long now = clock.instant().getEpochSecond();
        long window = now / policy.windowSeconds();
        int limit = policy.limitFor(operation);
        List<String> keys = bucketKeys(operation, context);

        for (String key : keys) {
            Counter counter = counters.get(key);
            int count = counter != null && counter.window == window ? counter.count : 0;
            if (count >= limit) {
                long retryAfter = Math.max(1, ((window + 1) * policy.windowSeconds()) - now);
                throw new RateLimitExceededException("비용 API 요청 한도를 초과했습니다.", retryAfter);
            }
        }

        for (String key : keys) {
            Counter counter = counters.get(key);
            if (counter == null || counter.window != window) {
                counters.put(key, new Counter(window, 1));
            } else {
                counter.count++;
            }
        }

        if (counters.size() > 10_000) {
            counters.entrySet().removeIf(entry -> entry.getValue().window < window - 1);
        }
    }

    private List<String> bucketKeys(CostOperation operation, CostRequestContext context) {
        List<String> keys = new ArrayList<>();
        keys.add(operation + ":owner:" + context.ownerKey());
        keys.add(operation + ":ip:" + normalize(context.clientIp()));
        if (context.sessionId() != null) {
            keys.add(operation + ":session:" + context.sessionId());
        }
        return keys;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static final class Counter {
        private final long window;
        private int count;

        private Counter(long window, int count) {
            this.window = window;
            this.count = count;
        }
    }
}
