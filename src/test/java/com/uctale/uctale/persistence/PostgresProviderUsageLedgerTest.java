package com.uctale.uctale.persistence;

import com.uctale.uctale.application.cost.ProviderCallEvent;
import com.uctale.uctale.application.cost.ProviderUsageStore;
import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PostgresProviderUsageLedgerTest extends PostgresIntegrationTestSupport {

    @Autowired private ProviderUsageStore usageStore;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanLedger() {
        jdbcTemplate.execute("truncate table provider_usage_event restart identity");
    }

    @Test
    @DisplayName("provider usage ledger는 재시작과 무관한 PostgreSQL 합계를 제공한다")
    void usageLedger_PersistsAndAggregatesBudgetUnits() {
        Instant first = Instant.parse("2026-08-31T01:00:00Z");
        Instant second = Instant.parse("2026-08-31T02:00:00Z");
        ProviderCallEvent narrative = new ProviderCallEvent(
                "gemini", "gemini-2.5-flash", "progress", 1L, 2, "request-1", null, 10, "SUCCESS", 0
        );
        ProviderCallEvent image = new ProviderCallEvent(
                "pollinations", "flux", "image_generation", 1L, 2, "request-2", null, 20, "FAILURE", 2
        );

        usageStore.record(first, narrative, 1);
        usageStore.record(second, image, 3);

        assertThat(usageStore.totalUnits(
                Instant.parse("2026-08-31T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z")
        )).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "select attempt_count from provider_usage_event where provider = 'pollinations'",
                Integer.class
        )).isEqualTo(3);
    }
}
