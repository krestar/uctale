package com.uctale.uctale.controller;

import com.uctale.uctale.security.AccessAuthenticationRateLimitPolicy;
import com.uctale.uctale.application.cost.ClientIpResolver;
import com.uctale.uctale.security.AccessAuthenticationRateLimiter;
import com.uctale.uctale.security.AccessSessionInterceptor;
import com.uctale.uctale.security.AccessSessionService;
import com.uctale.uctale.security.OwnerIdentityIssuanceRateLimitPolicy;
import com.uctale.uctale.security.OwnerIdentityIssuanceRateLimiter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccessControllerTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("올바른 비밀번호는 운영용 HttpOnly Secure 접근 쿠키를 발급한다")
    void verifyPassword_IssuesSecureHttpOnlyCookie() throws Exception {
        AccessSessionService service = new AccessSessionService("TEST_PASSWORD", SECRET, 3600, true);
        MockMvc mockMvc = standaloneMockMvc(service, limiter(5), issuanceLimiter(5));

        mockMvc.perform(post("/api/game/verify-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"TEST_PASSWORD\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("uctale_access=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=None")));
    }

    @Test
    @DisplayName("잘못된 비밀번호는 안정적인 401 오류를 반환한다")
    void verifyPassword_RejectsWrongPassword() throws Exception {
        AccessSessionService service = new AccessSessionService("TEST_PASSWORD", SECRET, 3600, false);
        MockMvc mockMvc = standaloneMockMvc(service, limiter(5));

        mockMvc.perform(post("/api/game/verify-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("같은 IP가 실패 한도에 도달하면 다음 인증부터 429와 Retry-After를 반환한다")
    void verifyPassword_RateLimitsRepeatedFailures() throws Exception {
        AccessSessionService service = new AccessSessionService("TEST_PASSWORD", SECRET, 3600, false);
        MockMvc mockMvc = standaloneMockMvc(service, limiter(2), issuanceLimiter(5));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/game/verify-password")
                            .with(request -> { request.setRemoteAddr("1.2.3.4"); return request; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/game/verify-password")
                        .with(request -> { request.setRemoteAddr("1.2.3.4"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"TEST_PASSWORD\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("ACCESS_RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("성공 인증은 같은 IP의 실패 기록을 초기화한다")
    void verifyPassword_SuccessResetsFailures() throws Exception {
        AccessSessionService service = new AccessSessionService("TEST_PASSWORD", SECRET, 3600, false);
        MockMvc mockMvc = standaloneMockMvc(service, limiter(2), issuanceLimiter(5));

        mockMvc.perform(post("/api/game/verify-password")
                        .with(request -> { request.setRemoteAddr("1.2.3.4"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/game/verify-password")
                        .with(request -> { request.setRemoteAddr("1.2.3.4"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"TEST_PASSWORD\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/game/verify-password")
                        .with(request -> { request.setRemoteAddr("1.2.3.4"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("올바른 비밀번호라도 같은 IP에서 새 owner identity 발급 한도를 넘으면 429로 차단한다")
    void verifyPassword_RateLimitsNewOwnerIdentityIssuance() throws Exception {
        AccessSessionService service = new AccessSessionService("TEST_PASSWORD", SECRET, 3600, false);
        MockMvc mockMvc = standaloneMockMvc(service, limiter(5), issuanceLimiter(2));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/game/verify-password")
                            .with(request -> { request.setRemoteAddr("1.2.3.4"); return request; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"TEST_PASSWORD\"}"))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(post("/api/game/verify-password")
                        .with(request -> { request.setRemoteAddr("1.2.3.4"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"TEST_PASSWORD\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("OWNER_ISSUANCE_RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("유효한 owner cookie 재인증은 새 owner 발급 quota를 소비하지 않는다")
    void verifyPassword_ExistingOwnerDoesNotConsumeIssuanceQuota() throws Exception {
        AccessSessionService service = new AccessSessionService("TEST_PASSWORD", SECRET, 3600, false);
        AccessSessionService.IssuedSession existing = service.authenticate("TEST_PASSWORD", null);
        MockMvc mockMvc = standaloneMockMvc(service, limiter(5), issuanceLimiter(1));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/game/verify-password")
                            .with(request -> { request.setRemoteAddr("1.2.3.4"); return request; })
                            .cookie(new Cookie(AccessSessionService.OWNER_COOKIE_NAME, existing.ownerToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"TEST_PASSWORD\"}"))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(post("/api/game/verify-password")
                        .with(request -> { request.setRemoteAddr("1.2.3.4"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"TEST_PASSWORD\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/game/verify-password")
                        .with(request -> { request.setRemoteAddr("1.2.3.4"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"TEST_PASSWORD\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OWNER_ISSUANCE_RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("만료된 접근 쿠키는 보호 API에서 401로 거부한다")
    void protectedEndpoint_RejectsExpiredSession() throws Exception {
        AccessSessionService service = new AccessSessionService("TEST_PASSWORD", SECRET, -1, false);
        String expiredToken = service.authenticate("TEST_PASSWORD");
        AccessSessionInterceptor interceptor = new AccessSessionInterceptor(service);
        MockMvc mockMvc = protectedMockMvc(service, interceptor);

        mockMvc.perform(get("/api/game/access-session")
                        .cookie(new Cookie(AccessSessionService.COOKIE_NAME, expiredToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCESS_SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("접근 쿠키가 없으면 보호 API에서 401로 거부한다")
    void protectedEndpoint_RejectsMissingSession() throws Exception {
        AccessSessionService service = new AccessSessionService("TEST_PASSWORD", SECRET, 3600, false);
        MockMvc mockMvc = protectedMockMvc(service, new AccessSessionInterceptor(service));

        mockMvc.perform(get("/api/game/access-session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCESS_SESSION_REQUIRED"));
    }

    @Test
    @DisplayName("유효한 쿠키가 있어도 client header가 없으면 403으로 거부한다")
    void protectedEndpoint_RejectsMissingClientHeader() throws Exception {
        AccessSessionService service = new AccessSessionService("TEST_PASSWORD", SECRET, 3600, false);
        String token = service.authenticate("TEST_PASSWORD");
        MockMvc mockMvc = protectedMockMvc(service, new AccessSessionInterceptor(service));

        mockMvc.perform(get("/api/game/access-session")
                        .cookie(new Cookie(AccessSessionService.COOKIE_NAME, token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_REQUEST_FORBIDDEN"));
    }

    @Test
    @DisplayName("유효한 쿠키와 client header가 있으면 보호 API를 호출한다")
    void protectedEndpoint_AcceptsSessionAndClientHeader() throws Exception {
        AccessSessionService service = new AccessSessionService("TEST_PASSWORD", SECRET, 3600, false);
        String token = service.authenticate("TEST_PASSWORD");
        MockMvc mockMvc = protectedMockMvc(service, new AccessSessionInterceptor(service));

        mockMvc.perform(get("/api/game/access-session")
                        .cookie(new Cookie(AccessSessionService.COOKIE_NAME, token))
                        .header(AccessSessionInterceptor.CLIENT_HEADER, AccessSessionInterceptor.CLIENT_HEADER_VALUE))
                .andExpect(status().isNoContent());
    }

    private MockMvc standaloneMockMvc(
            AccessSessionService service,
            AccessAuthenticationRateLimiter limiter,
            OwnerIdentityIssuanceRateLimiter issuanceLimiter
    ) {
        return MockMvcBuilders.standaloneSetup(
                        new AccessController(service, limiter, issuanceLimiter, new ClientIpResolver(false))
                )
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private MockMvc protectedMockMvc(AccessSessionService service, AccessSessionInterceptor interceptor) {
        return MockMvcBuilders.standaloneSetup(new AccessController(service, limiter(5), issuanceLimiter(5), new ClientIpResolver(false)))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(interceptor)
                .build();
    }

    private AccessAuthenticationRateLimiter limiter(int failureLimit) {
        return new AccessAuthenticationRateLimiter(
                new AccessAuthenticationRateLimitPolicy(failureLimit, 300),
                Clock.systemUTC()
        );
    }

    private OwnerIdentityIssuanceRateLimiter issuanceLimiter(int limit) {
        return new OwnerIdentityIssuanceRateLimiter(
                new OwnerIdentityIssuanceRateLimitPolicy(limit, 3600),
                Clock.systemUTC()
        );
    }
}
