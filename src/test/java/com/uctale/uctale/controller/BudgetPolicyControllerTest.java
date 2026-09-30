package com.uctale.uctale.controller;

import com.uctale.uctale.application.cost.ProviderBudgetPolicy;
import com.uctale.uctale.security.AccessSessionInterceptor;
import com.uctale.uctale.security.AccessSessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BudgetPolicyControllerTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("budget policy endpoint는 인증 없이 접근할 수 없다")
    void budgetPolicy_RequiresAccessSession() throws Exception {
        AccessSessionService accessSessionService =
                new AccessSessionService("TEST_PASSWORD", SECRET, 3600, false);
        MockMvc mockMvc = protectedMockMvc(accessSessionService);

        mockMvc.perform(get("/api/game/budget-policy"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCESS_SESSION_REQUIRED"));
    }

    @Test
    @DisplayName("budget policy endpoint는 인증된 요청에 effective production 정책 ID만 반환한다")
    void budgetPolicy_ReturnsEffectivePolicyId() throws Exception {
        AccessSessionService accessSessionService =
                new AccessSessionService("TEST_PASSWORD", SECRET, 3600, false);
        String accessToken = accessSessionService.authenticate("TEST_PASSWORD");
        MockMvc mockMvc = protectedMockMvc(accessSessionService);

        mockMvc.perform(get("/api/game/budget-policy")
                        .cookie(new Cookie(AccessSessionService.COOKIE_NAME, accessToken))
                        .header(AccessSessionInterceptor.CLIENT_HEADER, AccessSessionInterceptor.CLIENT_HEADER_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyId").value(ProviderBudgetPolicy.SHARED_BETA_V1_POLICY_ID));
    }

    private MockMvc protectedMockMvc(AccessSessionService accessSessionService) {
        ProviderBudgetPolicy policy = new ProviderBudgetPolicy(
                500, 750, 10_000, 15_000, 1, 1, "ALERT_ONLY"
        );
        return MockMvcBuilders.standaloneSetup(new BudgetPolicyController(policy))
                .addInterceptors(new AccessSessionInterceptor(accessSessionService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }
}
