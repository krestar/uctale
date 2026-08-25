package com.uctale.uctale.controller;

import com.uctale.uctale.security.AccessSessionInterceptor;
import com.uctale.uctale.security.AccessSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AccessController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

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
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AccessController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(post("/api/game/verify-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("만료된 접근 쿠키는 보호 API에서 401로 거부한다")
    void protectedEndpoint_RejectsExpiredSession() throws Exception {
        AccessSessionService service = new AccessSessionService("TEST_PASSWORD", SECRET, -1, false);
        String expiredToken = service.authenticate("TEST_PASSWORD");
        AccessSessionInterceptor interceptor = new AccessSessionInterceptor(service);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AccessController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(interceptor)
                .build();

        mockMvc.perform(get("/api/game/access-session")
                        .cookie(new jakarta.servlet.http.Cookie(AccessSessionService.COOKIE_NAME, expiredToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCESS_SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("접근 쿠키가 없으면 보호 API에서 401로 거부한다")
    void protectedEndpoint_RejectsMissingSession() throws Exception {
        AccessSessionService service = new AccessSessionService("TEST_PASSWORD", SECRET, 3600, false);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AccessController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new AccessSessionInterceptor(service))
                .build();

        mockMvc.perform(get("/api/game/access-session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCESS_SESSION_REQUIRED"));
    }
}
