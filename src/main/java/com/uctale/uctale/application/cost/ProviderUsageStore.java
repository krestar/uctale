package com.uctale.uctale.application.cost;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class ProviderUsageStore {

    private final JdbcTemplate jdbcTemplate;

    public ProviderUsageStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(Instant occurredAt, ProviderCallEvent event, long budgetUnits) {
        jdbcTemplate.update(
                """
                insert into provider_usage_event (
                    occurred_at, provider, model, operation, outcome, attempt_count, budget_units
                ) values (?, ?, ?, ?, ?, ?, ?)
                """,
                Timestamp.from(occurredAt),
                event.provider(),
                event.model(),
                event.operation(),
                event.outcome(),
                event.attemptCount(),
                budgetUnits
        );
    }

    public long totalUnits(Instant fromInclusive, Instant toExclusive) {
        Long result = jdbcTemplate.queryForObject(
                """
                select coalesce(sum(budget_units), 0)
                from provider_usage_event
                where occurred_at >= ? and occurred_at < ?
                """,
                Long.class,
                Timestamp.from(fromInclusive),
                Timestamp.from(toExclusive)
        );
        return result == null ? 0 : result;
    }
}
