package com.uctale.uctale.controller;

import com.uctale.uctale.application.narrative.NarrativeProviderException;
import com.uctale.uctale.application.narrative.NarrativeRecoveryDeferredException;
import com.uctale.uctale.application.narrative.NarrativeRecoveryExhaustedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("provider recovery 대기는 503과 Retry-After를 함께 반환한다")
    void handleNarrativeRecoveryDeferred_ReturnsRetryAfter() {
        NarrativeRecoveryDeferredException exception = new NarrativeRecoveryDeferredException(
                2,
                "PROVIDER_HTTP_503",
                30,
                new IllegalStateException("provider unavailable")
        );

        ResponseEntity<ApiError> response = handler.handleNarrativeRecoveryDeferred(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("30");
        assertThat(response.getBody()).isEqualTo(new ApiError(
                "NARRATIVE_PROVIDER_RECOVERY_WAIT",
                "Narrative provider 복구를 기다리고 있습니다. 잠시 후 다시 시도해 주세요."
        ));
    }

    @Test
    @DisplayName("provider transient recovery 소진은 generic 500 대신 503으로 응답한다")
    void handleNarrativeRecoveryExhausted_ProviderTransient_ReturnsServiceUnavailable() {
        NarrativeRecoveryExhaustedException exception = new NarrativeRecoveryExhaustedException(
                2,
                "PROVIDER_HTTP_503",
                new IllegalStateException("provider unavailable")
        );

        ResponseEntity<ApiError> response = handler.handleNarrativeRecoveryExhausted(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(new ApiError(
                "NARRATIVE_PROVIDER_UNAVAILABLE",
                "Narrative provider가 일시적으로 응답하지 않습니다."
        ));
    }

    @Test
    @DisplayName("retry 대상이 아닌 provider 실패는 generic 500 대신 502로 응답한다")
    void handleNarrativeProvider_ReturnsBadGateway() {
        NarrativeProviderException exception = new NarrativeProviderException(
                "provider rejected request",
                new IllegalStateException("bad request")
        );

        ResponseEntity<ApiError> response = handler.handleNarrativeProvider(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isEqualTo(new ApiError(
                "NARRATIVE_PROVIDER_FAILURE",
                "Narrative provider 요청에 실패했습니다."
        ));
    }
}
