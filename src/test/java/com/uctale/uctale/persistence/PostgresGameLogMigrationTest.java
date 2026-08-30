package com.uctale.uctale.persistence;

import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresGameLogMigrationTest extends PostgresIntegrationTestSupport {

    @Test
    @DisplayName("V5 legacy GameLog 선택 텍스트를 다음 committed turn 자기 행으로 이관한다")
    void v6_MigratesLegacyUserChoiceIntoCommittedTurnRow() throws Exception {
        String schema = "game_log_v6_legacy";
        Flyway legacyFlyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("5"))
                .load();
        legacyFlyway.migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            statement.executeUpdate("""
                    insert into game_session (
                        owner_key, world_setting, character_setting, is_game_over, current_turn, version
                    ) values (
                        'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA', '세계관', '캐릭터', false, 2, 1
                    )
                    """);
            statement.executeUpdate("""
                    insert into game_log (
                        session_id, turn_number, story_text, choices_json, user_choice
                    ) values (
                        1, 1, '첫 이야기', '[{"id":7,"text":"문을 연다"}]', '문을 연다'
                    )
                    """);
            statement.executeUpdate("""
                    insert into game_log (
                        session_id, turn_number, story_text, choices_json, user_choice
                    ) values (
                        1, 2, '두 번째 이야기', '[{"id":3,"text":"살핀다"}]', null
                    )
                    """);
        }

        Flyway latestFlyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .load();
        latestFlyway.migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);

            try (ResultSet opening = statement.executeQuery("""
                    select input_choice_id, input_choice_text, previous_state_version, state_version
                    from game_log where turn_number = 1
                    """)) {
                assertThat(opening.next()).isTrue();
                assertThat(opening.getObject("input_choice_id")).isNull();
                assertThat(opening.getString("input_choice_text")).isNull();
                assertThat(opening.getInt("previous_state_version")).isZero();
                assertThat(opening.getInt("state_version")).isEqualTo(1);
            }

            try (ResultSet secondTurn = statement.executeQuery("""
                    select input_choice_id, input_choice_text, previous_state_version, state_version
                    from game_log where turn_number = 2
                    """)) {
                assertThat(secondTurn.next()).isTrue();
                assertThat(secondTurn.getObject("input_choice_id")).isNull();
                assertThat(secondTurn.getString("input_choice_text")).isEqualTo("문을 연다");
                assertThat(secondTurn.getInt("previous_state_version")).isEqualTo(1);
                assertThat(secondTurn.getInt("state_version")).isEqualTo(2);
            }

            try (ResultSet legacyColumn = statement.executeQuery("""
                    select user_choice from game_log where turn_number = 1
                    """)) {
                assertThat(legacyColumn.next()).isTrue();
                assertThat(legacyColumn.getString("user_choice")).isEqualTo("문을 연다");
            }
        }
    }
}
