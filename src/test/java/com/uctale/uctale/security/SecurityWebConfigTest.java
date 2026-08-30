package com.uctale.uctale.security;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityWebConfigTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("명시된 Vercel origin만 보호 client header credential CORS 응답을 받는다")
    void cors_AllowsConfiguredOriginOnly() throws Exception {
        SecurityWebConfig config = config();
        CorsFilter filter = config.corsFilterRegistration("https://uctale.vercel.app").getFilter();

        MockHttpServletRequest allowed = preflight("https://uctale.vercel.app");
        MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
        filter.doFilter(allowed, allowedResponse, new MockFilterChain());
        assertThat(allowedResponse.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://uctale.vercel.app");
        assertThat(allowedResponse.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(allowedResponse.getHeader("Access-Control-Allow-Headers"))
                .containsIgnoringCase(AccessSessionInterceptor.CLIENT_HEADER);

        MockHttpServletRequest denied = preflight("https://evil.example");
        MockHttpServletResponse deniedResponse = new MockHttpServletResponse();
        filter.doFilter(denied, deniedResponse, new MockFilterChain());
        assertThat(deniedResponse.getHeader("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    @DisplayName("image asset 오류 dispatch에도 허용된 origin의 credential CORS 응답을 유지한다")
    void cors_ErrorDispatchKeepsAllowedOrigin() throws Exception {
        CorsFilter filter = config()
                .corsFilterRegistration("https://uctale.vercel.app")
                .getFilter();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/game/image-assets/asset-id");
        request.setDispatcherType(DispatcherType.ERROR);
        request.addHeader("Origin", "https://uctale.vercel.app");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://uctale.vercel.app");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
    }

    @Test
    @DisplayName("와일드카드 CORS 설정은 시작 단계에서 거부한다")
    void cors_RejectsWildcard() {
        assertThatThrownBy(() -> config().corsFilterRegistration("*"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("서명된 접근 토큰은 변조되면 거부된다")
    void session_RejectsTamperedToken() {
        AccessSessionService service = new AccessSessionService("pw", SECRET, 3600, true);
        String token = service.authenticate("pw");
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        assertThatThrownBy(() -> service.validate(tampered))
                .isInstanceOf(AccessSessionException.class)
                .extracting("code")
                .isEqualTo("ACCESS_SESSION_INVALID");
    }

    private SecurityWebConfig config() {
        AccessSessionService service = new AccessSessionService("pw", SECRET, 3600, true);
        return new SecurityWebConfig(new AccessSessionInterceptor(service));
    }

    private MockHttpServletRequest preflight(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/game/init");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "Content-Type, " + AccessSessionInterceptor.CLIENT_HEADER);
        return request;
    }
}
