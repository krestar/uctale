package com.uctale.uctale.application.cost;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

@Component
public class ProviderBudgetGuard {

    private final ProviderUsageStore usageStore;
    private final ProviderBudgetPolicy policy;
    private final Clock clock;

    public ProviderBudgetGuard(ProviderUsageStore usageStore, ProviderBudgetPolicy policy, Clock clock) {
        this.usageStore = usageStore;
        this.policy = policy;
        this.clock = clock;
    }

    public void checkBeforeCall(String operation) {
        if (policy.criticalMode() != ProviderBudgetPolicy.CriticalMode.FAIL_CLOSED) {
            return;
        }

        long nextAttemptUnits = policy.unitsPerAttempt(operation);
        Usage usage = currentUsage();
        if (usage.dailyUnits() + nextAttemptUnits > policy.dailyCriticalUnits()
                || usage.monthlyUnits() + nextAttemptUnits > policy.monthlyCriticalUnits()) {
            throw new ProviderBudgetExceededException("AI 전역 비용 예산을 초과해 신규 provider 호출을 차단했습니다.");
        }
    }

    public Usage currentUsage() {
        Instant now = clock.instant();
        LocalDate utcDate = now.atZone(ZoneOffset.UTC).toLocalDate();
        YearMonth utcMonth = YearMonth.from(utcDate);
        Instant dayStart = utcDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant nextDayStart = utcDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant monthStart = utcMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant nextMonthStart = utcMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new Usage(
                usageStore.totalUnits(dayStart, nextDayStart),
                usageStore.totalUnits(monthStart, nextMonthStart)
        );
    }

    public record Usage(long dailyUnits, long monthlyUnits) {}
}
