package com.uctale.uctale.application.cost;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ClientIpResolverTest {

    @Test
    @DisplayName("X-Forwarded-For가 있으면 첫 client 주소를 사용한다")
    void forwardedFor_UsesFirstAddress() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getHeader("X-Forwarded-For")).willReturn("203.0.113.10, 10.0.0.3");
        given(request.getRemoteAddr()).willReturn("10.0.0.2");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("proxy header가 없으면 remote address를 사용한다")
    void noForwardedFor_UsesRemoteAddress() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getRemoteAddr()).willReturn("198.51.100.7");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("198.51.100.7");
    }
}
