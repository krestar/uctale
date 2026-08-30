package com.uctale.uctale.application.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class GameSessionOwnershipMigrationTest {

    @Test
    @DisplayName("기존 익명 세션은 V3 migration에서 선점 불가능한 legacy owner로 백필된다")
    void migration_BackfillsLegacyOwnerAndMakesColumnNotNull() throws Exception {
        String url = "jdbc:h2:mem:ownership_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__create_game_tables.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V2__create_game_state_snapshot.sql"));

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        insert into game_session (
                            world_setting, character_setting, is_game_over, current_turn, version
                        ) values ('기존 세계관', '기존 캐릭터', false, 1, 0)
                        """);
            }

            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V3__add_game_session_owner.sql"));

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("select id, owner_key from game_session")) {
                assertThat(resultSet.next()).isTrue();
                long id = resultSet.getLong("id");
                assertThat(resultSet.getString("owner_key")).isEqualTo("legacy-" + id);
                assertThat(resultSet.next()).isFalse();
            }

            try (Statement statement = connection.createStatement()) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> statement.executeUpdate("""
                                insert into game_session (
                                    owner_key, world_setting, character_setting, is_game_over, current_turn, version
                                ) values (null, '새 세계관', '새 캐릭터', false, 1, 0)
                                """))
                        .isInstanceOf(java.sql.SQLException.class);
            }
        }
    }
}
