package com.uctale.uctale.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccessSessionOwnerTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("재로그인은 장기 owner 쿠키의 owner key를 재사용한다")
    void authenticate_ReusesOwnerKeyFromOwnerCookie() {
        AccessSessionService service = new AccessSessionService("TEST_PASSWORD", SECRET, 3600, false);

        AccessSessionService.IssuedSession first = service.authenticate("TEST_PASSWORD", null);
        AccessSessionService.IssuedSession renewed = service.authenticate("TEST_PASSWORD", first.ownerToken());

        assertThat(renewed.ownerKey()).isEqualTo(first.ownerKey());
        assertThat(service.validateAndGetPrincipal(renewed.accessToken()).ownerKey()).isEqualTo(first.ownerKey());
    }

    @Test
    @DisplayName("기존 access 쿠키만 있는 보호 요청은 같은 owner key의 owner 쿠키를 승격 발급한다")
    void interceptor_PromotesLegacyAccessTokenToOwnerCookie() throws Exception {
        AccessSessionService service = new AccessSessionService("TEST_PASSWORD", SECRET, 3600, false);
        String accessToken = service.authenticate("TEST_PASSWORD");
        String ownerKey = service.validateAndGetPrincipal(accessToken).ownerKey();
        AccessSessionInterceptor interceptor = new AccessSessionInterceptor(service);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .addInterceptors(interceptor)
                .build();

        mockMvc.perform(get("/protected")
                        .cookie(new Cookie(AccessSessionService.COOKIE_NAME, accessToken))
                        .header(AccessSessionInterceptor.CLIENT_HEADER, AccessSessionInterceptor.CLIENT_HEADER_VALUE))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString(AccessSessionService.OWNER_COOKIE_NAME + "=")))
                .andExpect(header().string("Set-Cookie", containsString(service.issueOwnerToken(ownerKey))));
    }

    @org.springframework.web.bind.annotation.RestController
    static class TestController {
        @org.springframework.web.bind.annotation.GetMapping("/protected")
        org.springframework.http.ResponseEntity<Void> protectedEndpoint() {
            return org.springframework.http.ResponseEntity.noContent().build();
        }
    }
}
