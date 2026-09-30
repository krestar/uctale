package com.uctale.uctale.controller;

import com.uctale.uctale.application.cost.ProviderBudgetPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BudgetPolicyControllerTest {

    @Test
    @DisplayName("budget policy endpoint는 effective production 정책 ID만 반환한다")
    void budgetPolicy_ReturnsEffectivePolicyId() throws Exception {
        ProviderBudgetPolicy policy = new ProviderBudgetPolicy(
                500, 750, 10_000, 15_000, 1, 1, "ALERT_ONLY"
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new BudgetPolicyController(policy)).build();

        mockMvc.perform(get("/api/game/budget-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyId").value(ProviderBudgetPolicy.SHARED_BETA_V1_POLICY_ID));
    }
}
