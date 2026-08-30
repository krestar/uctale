package com.uctale.uctale.controller;

import com.uctale.uctale.application.cost.RateLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerRateLimitTest {

    @Test
    @DisplayName("rate limit 초과는 429와 Retry-After를 반환한다")
    void rateLimit_ReturnsStableContract() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        var response = handler.handleRateLimit(new RateLimitExceededException("비용 API 요청 한도를 초과했습니다.", 17));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("17");
        assertThat(response.getBody()).isEqualTo(new ApiError("RATE_LIMIT_EXCEEDED", "비용 API 요청 한도를 초과했습니다."));
    }
}
