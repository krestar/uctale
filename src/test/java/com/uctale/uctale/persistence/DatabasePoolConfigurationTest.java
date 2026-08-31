package com.uctale.uctale.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.config.location=file:src/main/resources/application.properties",
        "GOOGLE_AI_API_KEY=TEST_API_KEY",
        "POLLINATIONS_TOKEN=TEST_TOKEN",
        "GAME_ACCESS_PASSWORD=TEST_PASSWORD",
        "GAME_ACCESS_SESSION_SECRET=TEST_SESSION_SECRET_0123456789_0123456789",
        "DATABASE_URL=jdbc:h2:mem:poolconfig;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "DATABASE_USERNAME=sa",
        "DATABASE_PASSWORD=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"
})
class DatabasePoolConfigurationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void productionDefaultsAllowIdlePoolToDrainBeforeNeonSuspendWindow() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        HikariDataSource hikari = (HikariDataSource) dataSource;

        assertThat(hikari.getKeepaliveTime()).isZero();
        assertThat(hikari.getMinimumIdle()).isZero();
        assertThat(hikari.getMaximumPoolSize()).isEqualTo(3);
        assertThat(hikari.getIdleTimeout()).isEqualTo(60_000L);
        assertThat(hikari.getIdleTimeout()).isLessThan(5 * 60_000L);
    }
}
