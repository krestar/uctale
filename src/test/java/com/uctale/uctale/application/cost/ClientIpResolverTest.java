package com.uctale.uctale.application.cost;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ClientIpResolverTest {

    @Test
    @DisplayName("기본 설정은 caller supplied forwarded header를 무시하고 remote address를 사용한다")
    void defaultMode_IgnoresForwardedHeaders() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getHeader("CF-Connecting-IP")).willReturn("203.0.113.10");
        given(request.getHeader("X-Forwarded-For")).willReturn("198.51.100.4, 10.0.0.3");
        given(request.getRemoteAddr()).willReturn("10.0.0.2");

        assertThat(new ClientIpResolver(false).resolve(request)).isEqualTo("10.0.0.2");
    }

    @Test
    @DisplayName("Render proxy trust 설정에서는 edge가 덮어쓰는 CF-Connecting-IP를 사용한다")
    void renderProxyMode_UsesCloudflareConnectingIp() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getHeader("CF-Connecting-IP")).willReturn("203.0.113.10");
        given(request.getHeader("X-Forwarded-For")).willReturn("198.51.100.4, 10.0.0.3");
        given(request.getRemoteAddr()).willReturn("10.0.0.2");

        assertThat(new ClientIpResolver(true).resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("Render proxy trust 설정이어도 전용 header가 없으면 X-Forwarded-For 대신 remote address로 fallback한다")
    void renderProxyMode_DoesNotTrustForwardedForFallback() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getHeader("X-Forwarded-For")).willReturn("198.51.100.4, 10.0.0.3");
        given(request.getRemoteAddr()).willReturn("10.0.0.2");

        assertThat(new ClientIpResolver(true).resolve(request)).isEqualTo("10.0.0.2");
    }
}
