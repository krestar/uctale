package com.uctale.uctale.controller;

import com.uctale.uctale.application.cost.ProviderBudgetPolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/game")
public class BudgetPolicyController {

    private final ProviderBudgetPolicy providerBudgetPolicy;

    public BudgetPolicyController(ProviderBudgetPolicy providerBudgetPolicy) {
        this.providerBudgetPolicy = providerBudgetPolicy;
    }

    @GetMapping("/budget-policy")
    public BudgetPolicyStatus budgetPolicy() {
        return new BudgetPolicyStatus(providerBudgetPolicy.effectivePolicyId());
    }

    public record BudgetPolicyStatus(String policyId) {}
}
