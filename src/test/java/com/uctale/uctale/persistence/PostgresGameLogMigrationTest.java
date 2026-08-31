package com.uctale.uctale.persistence;

import com.uctale.uctale.application.game.GameStateCodec;
import com.uctale.uctale.application.game.GameStateUpgrader;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresGameLogMigrationTest extends PostgresIntegrationTestSupport {

    private static final String LEGACY_SNAPSHOT_FIXTURE = "fixtures/game-state/snapshot-v0-production.json";

    @Test
    @DisplayName("V5 legacy GameLog 선택 텍스트를 다음 committed turn 자기 행으로 이관한다")
    void v6_MigratesLegacyUserChoiceIntoCommittedTurnRow() throws Exception {
        String schema = "game_log_v6_legacy";
        Flyway legacyFlyway = flywayAt(schema, "5");
        legacyFlyway.migrate();

        try (Connection connection = connection(schema); Statement statement = connection.createStatement()) {
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

        latestFlyway(schema).migrate();

        try (Connection connection = connection(schema); Statement statement = connection.createStatement()) {
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

    @Test
    @DisplayName("V5 production 형태 fixture는 V8까지 migration 후 ledger와 legacy snapshot을 데이터 손실 없이 복구한다")
    void legacyV5Fixture_MigratesThroughLatestAndRemainsRecoverable() throws Exception {
        String schema = "m2_legacy_v5_to_latest";
        flywayAt(schema, "5").migrate();
        String legacySnapshot = legacySnapshotJson();

        try (Connection connection = connection(schema); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into game_session (
                        owner_key, world_setting, character_setting, is_game_over, current_turn, version
                    ) values (
                        'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA', '세계관', '캐릭터', false, 1, 0
                    )
                    """);
            statement.executeUpdate("""
                    insert into game_log (
                        session_id, turn_number, story_text, choices_json, user_choice
                    ) values (
                        1, 1, '첫 이야기', '[{"id":7,"text":"문을 연다"}]', null
                    )
                    """);
            try (var prepared = connection.prepareStatement(
                    "insert into game_state_snapshot (session_id, state_json) values (1, ?)"
            )) {
                prepared.setString(1, legacySnapshot);
                prepared.executeUpdate();
            }
        }

        Flyway latest = latestFlyway(schema);
        latest.migrate();

        assertThat(latest.info().pending()).as("schema=%s pending migrations", schema).isEmpty();
        assertThat(latest.info().applied())
                .as("schema=%s applied migration chain", schema)
                .extracting(info -> info.getVersion().getVersion())
                .contains("1", "2", "3", "4", "5", "6", "7", "8");

        try (Connection connection = connection(schema); Statement statement = connection.createStatement()) {
            try (ResultSet ledger = statement.executeQuery("""
                    select story_text, previous_state_version, state_version
                    from game_log where session_id = 1 and turn_number = 1
                    """)) {
                assertThat(ledger.next()).as("schema=%s session=1 turn=1 ledger row", schema).isTrue();
                assertThat(ledger.getString("story_text")).isEqualTo("첫 이야기");
                assertThat(ledger.getInt("previous_state_version")).isZero();
                assertThat(ledger.getInt("state_version")).isEqualTo(1);
            }

            String storedSnapshot;
            try (ResultSet snapshot = statement.executeQuery(
                    "select state_json from game_state_snapshot where session_id = 1"
            )) {
                assertThat(snapshot.next()).as("schema=%s session=1 legacy snapshot", schema).isTrue();
                storedSnapshot = snapshot.getString(1);
            }
            assertThat(storedSnapshot).isEqualTo(legacySnapshot);

            GameState recovered = new GameStateCodec(new ObjectMapper(), new GameStateUpgrader())
                    .deserialize(storedSnapshot);
            assertThat(recovered.turnNumber()).as("schema=%s recovered snapshot turn", schema).isEqualTo(1);
            assertThat(recovered.worldState().premise()).isEqualTo("세계관");
            assertThat(recovered.playerCharacter().description()).isEqualTo("캐릭터");

            assertThat(tableExists(statement, "game_mutation_request"))
                    .as("schema=%s V7 table", schema).isTrue();
            assertThat(tableExists(statement, "game_turn_reservation"))
                    .as("schema=%s V8 table", schema).isTrue();
        }
    }

    private Flyway flywayAt(String schema, String version) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion(version))
                .load();
    }

    private Flyway latestFlyway(String schema) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .load();
    }

    private Connection connection(String schema) throws Exception {
        Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        );
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
        }
        return connection;
    }

    private boolean tableExists(Statement statement, String tableName) throws Exception {
        try (ResultSet result = statement.executeQuery(
                "select to_regclass('" + tableName + "') is not null"
        )) {
            return result.next() && result.getBoolean(1);
        }
    }

    private String legacySnapshotJson() throws Exception {
        ClassPathResource resource = new ClassPathResource(LEGACY_SNAPSHOT_FIXTURE);
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
