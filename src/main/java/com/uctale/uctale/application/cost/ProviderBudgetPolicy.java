package com.uctale.uctale.application.cost;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProviderBudgetPolicy {

    public static final String SHARED_BETA_V1_POLICY_ID = "shared-beta-v1";
    private static final String CUSTOM_POLICY_ID = "custom";
    private static final long SHARED_BETA_DAILY_WARNING_UNITS = 500L;
    private static final long SHARED_BETA_DAILY_CRITICAL_UNITS = 750L;
    private static final long SHARED_BETA_MONTHLY_WARNING_UNITS = 10_000L;
    private static final long SHARED_BETA_MONTHLY_CRITICAL_UNITS = 15_000L;
    private static final long SHARED_BETA_NARRATIVE_ATTEMPT_UNITS = 1L;
    private static final long SHARED_BETA_IMAGE_ATTEMPT_UNITS = 1L;

    public enum CriticalMode {
        ALERT_ONLY,
        FAIL_CLOSED
    }

    private final long dailyWarningUnits;
    private final long dailyCriticalUnits;
    private final long monthlyWarningUnits;
    private final long monthlyCriticalUnits;
    private final long narrativeAttemptUnits;
    private final long imageAttemptUnits;
    private final CriticalMode criticalMode;

    public ProviderBudgetPolicy(
            @Value("${game.cost.budget.daily-warning-units:500}") long dailyWarningUnits,
            @Value("${game.cost.budget.daily-critical-units:750}") long dailyCriticalUnits,
            @Value("${game.cost.budget.monthly-warning-units:10000}") long monthlyWarningUnits,
            @Value("${game.cost.budget.monthly-critical-units:15000}") long monthlyCriticalUnits,
            @Value("${game.cost.budget.narrative-attempt-units:1}") long narrativeAttemptUnits,
            @Value("${game.cost.budget.image-attempt-units:1}") long imageAttemptUnits,
            @Value("${game.cost.budget.critical-mode:ALERT_ONLY}") String criticalMode
    ) {
        if (dailyWarningUnits <= 0 || dailyCriticalUnits <= 0 || monthlyWarningUnits <= 0 || monthlyCriticalUnits <= 0) {
            throw new IllegalArgumentException("AI budget threshold는 1 이상이어야 합니다.");
        }
        if (dailyWarningUnits > dailyCriticalUnits || monthlyWarningUnits > monthlyCriticalUnits) {
            throw new IllegalArgumentException("AI budget warning threshold는 critical threshold 이하여야 합니다.");
        }
        if (narrativeAttemptUnits <= 0 || imageAttemptUnits <= 0) {
            throw new IllegalArgumentException("AI budget attempt unit은 1 이상이어야 합니다.");
        }
        this.dailyWarningUnits = dailyWarningUnits;
        this.dailyCriticalUnits = dailyCriticalUnits;
        this.monthlyWarningUnits = monthlyWarningUnits;
        this.monthlyCriticalUnits = monthlyCriticalUnits;
        this.narrativeAttemptUnits = narrativeAttemptUnits;
        this.imageAttemptUnits = imageAttemptUnits;
        this.criticalMode = CriticalMode.valueOf(criticalMode.trim().toUpperCase());
    }

    public long unitsPerAttempt(String operation) {
        return "image_generation".equals(operation) ? imageAttemptUnits : narrativeAttemptUnits;
    }

    public long dailyWarningUnits() { return dailyWarningUnits; }
    public long dailyCriticalUnits() { return dailyCriticalUnits; }
    public long monthlyWarningUnits() { return monthlyWarningUnits; }
    public long monthlyCriticalUnits() { return monthlyCriticalUnits; }
    public CriticalMode criticalMode() { return criticalMode; }

    public String effectivePolicyId() {
        boolean sharedBetaV1 = dailyWarningUnits == SHARED_BETA_DAILY_WARNING_UNITS
                && dailyCriticalUnits == SHARED_BETA_DAILY_CRITICAL_UNITS
                && monthlyWarningUnits == SHARED_BETA_MONTHLY_WARNING_UNITS
                && monthlyCriticalUnits == SHARED_BETA_MONTHLY_CRITICAL_UNITS
                && narrativeAttemptUnits == SHARED_BETA_NARRATIVE_ATTEMPT_UNITS
                && imageAttemptUnits == SHARED_BETA_IMAGE_ATTEMPT_UNITS
                && criticalMode == CriticalMode.ALERT_ONLY;
        return sharedBetaV1 ? SHARED_BETA_V1_POLICY_ID : CUSTOM_POLICY_ID;
    }
}
