package com.uctale.uctale.application.cost;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Slf4j
@Component
public class LoggingProviderCallEventSink implements ProviderCallEventSink {

    private final ProviderUsageStore usageStore;
    private final ProviderBudgetPolicy policy;
    private final ProviderBudgetGuard budgetGuard;
    private final Clock clock;

    public LoggingProviderCallEventSink(
            ProviderUsageStore usageStore,
            ProviderBudgetPolicy policy,
            ProviderBudgetGuard budgetGuard,
            Clock clock
    ) {
        this.usageStore = usageStore;
        this.policy = policy;
        this.budgetGuard = budgetGuard;
        this.clock = clock;
    }

    @Override
    public void record(ProviderCallEvent event) {
        log.info(
                "provider_call provider={} model={} operation={} sessionId={} turn={} requestId={} idempotencyKey={} latencyMs={} outcome={} retryCount={} attemptCount={}",
                event.provider(),
                event.model(),
                event.operation(),
                event.sessionId(),
                event.turn(),
                event.requestId(),
                event.idempotencyKey() == null ? "-" : event.idempotencyKey(),
                event.latencyMs(),
                event.outcome(),
                event.retryCount(),
                event.attemptCount()
        );

        long budgetUnits = Math.multiplyExact(policy.unitsPerAttempt(event.operation()), event.attemptCount());
        try {
            ProviderBudgetGuard.Usage before = budgetGuard.currentUsage();
            usageStore.record(clock.instant(), event, budgetUnits);
            ProviderBudgetGuard.Usage after = budgetGuard.currentUsage();
            alertIfThresholdCrossed(before, after, event);
        } catch (DataAccessException exception) {
            log.error(
                    "ai_budget_alert level=ACCOUNTING_FAILURE provider={} model={} operation={} outcome={} attemptCount={} error={}",
                    event.provider(), event.model(), event.operation(), event.outcome(), event.attemptCount(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void alertIfThresholdCrossed(
            ProviderBudgetGuard.Usage before,
            ProviderBudgetGuard.Usage after,
            ProviderCallEvent event
    ) {
        if (crossed(before.dailyUnits(), after.dailyUnits(), policy.dailyCriticalUnits())
                || crossed(before.monthlyUnits(), after.monthlyUnits(), policy.monthlyCriticalUnits())) {
            log.error(
                    "ai_budget_alert level=CRITICAL provider={} model={} operation={} dailyUnits={} dailyCritical={} monthlyUnits={} monthlyCritical={} mode={}",
                    event.provider(), event.model(), event.operation(), after.dailyUnits(), policy.dailyCriticalUnits(),
                    after.monthlyUnits(), policy.monthlyCriticalUnits(), policy.criticalMode()
            );
            return;
        }
        if (crossed(before.dailyUnits(), after.dailyUnits(), policy.dailyWarningUnits())
                || crossed(before.monthlyUnits(), after.monthlyUnits(), policy.monthlyWarningUnits())) {
            log.warn(
                    "ai_budget_alert level=WARNING provider={} model={} operation={} dailyUnits={} dailyWarning={} monthlyUnits={} monthlyWarning={}",
                    event.provider(), event.model(), event.operation(), after.dailyUnits(), policy.dailyWarningUnits(),
                    after.monthlyUnits(), policy.monthlyWarningUnits()
            );
        }
    }

    private boolean crossed(long before, long after, long threshold) {
        return before < threshold && after >= threshold;
    }
}
