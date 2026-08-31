package com.uctale.uctale.persistence;

import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.GameTurnCommit;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.domain.game.CharacterStats;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PostgresGameStateSnapshotCompatibilityTest extends PostgresIntegrationTestSupport {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String LEGACY_FIXTURE = "fixtures/game-state/snapshot-v0-production.json";

    @Autowired private GamePersistenceService gamePersistenceService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("truncate table image_asset, game_state_snapshot, game_log, game_session restart identity cascade");
    }

    @Test
    @DisplayName("기존 production raw snapshot을 기본 typed stats로 읽고 다음 write에서 schema v2로 저장한다")
    void legacyRawSnapshot_IsReadAndRewrittenOnNextCanonicalWrite() throws Exception {
        GameSession session = gamePersistenceService.saveOpening(
                OWNER_KEY, "세계관", "캐릭터", "첫 이야기", "[]", null
        );
        String openingSnapshotJson = jdbcTemplate.queryForObject(
                "select state_json from game_state_snapshot where session_id = ?",
                String.class,
                session.getId()
        );
        JsonNode openingSnapshot = objectMapper.readTree(openingSnapshotJson);
        assertThat(openingSnapshot.get("schemaVersion").asInt()).isEqualTo(2);
        assertThat(openingSnapshot.get("rulesetVersion").asInt()).isEqualTo(1);
        assertThat(openingSnapshot.get("state").get("turnNumber").asInt()).isEqualTo(1);
        assertThat(openingSnapshot.get("state").get("playerCharacter").get("stats").get("might").asInt())
                .isEqualTo(CharacterStats.DEFAULT_SCORE);

        GameState initialState = gamePersistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 1).gameState();
        String legacyRawJson = legacyStateJson();
        jdbcTemplate.update(
                "update game_state_snapshot set state_json = ? where session_id = ?",
                legacyRawJson,
                session.getId()
        );

        GameState recovered = gamePersistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 1).gameState();

        assertThat(recovered).isEqualTo(initialState);
        assertThat(recovered.playerCharacter().stats()).isEqualTo(CharacterStats.defaults());
        assertThat(jdbcTemplate.queryForObject(
                "select state_json from game_state_snapshot where session_id = ?",
                String.class,
                session.getId()
        )).isEqualTo(legacyRawJson);

        GameState nextState = recovered.advance("진행", "두 번째 이야기");
        gamePersistenceService.saveNextTurn(
                OWNER_KEY,
                session.getId(),
                new GameTurnCommit(1, 1, "진행", recovered, nextState, "두 번째 이야기", "[]", null)
        );

        String rewrittenJson = jdbcTemplate.queryForObject(
                "select state_json from game_state_snapshot where session_id = ?",
                String.class,
                session.getId()
        );
        JsonNode rewritten = objectMapper.readTree(rewrittenJson);
        assertThat(rewritten.get("schemaVersion").asInt()).isEqualTo(2);
        assertThat(rewritten.get("rulesetVersion").asInt()).isEqualTo(1);
        assertThat(rewritten.get("state").get("turnNumber").asInt()).isEqualTo(2);
        assertThat(rewritten.get("state").get("playerCharacter").get("stats").get("presence").asInt())
                .isEqualTo(CharacterStats.DEFAULT_SCORE);
    }

    @Test
    @DisplayName("snapshot 없는 기존 session은 append-only GameLog 원장에서 현재 GameState와 기본 stats를 복구한다")
    void snapshotlessSession_RecoversFromCommittedTurnLedger() {
        GameSession session = gamePersistenceService.saveOpening(
                OWNER_KEY, "세계관", "캐릭터", "첫 이야기", "[]", null
        );
        GameState initialState = gamePersistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 1).gameState();
        GameState nextState = initialState.advance("진행", "두 번째 이야기");
        gamePersistenceService.saveNextTurn(
                OWNER_KEY,
                session.getId(),
                new GameTurnCommit(1, 1, "진행", initialState, nextState, "두 번째 이야기", "[]", null)
        );
        jdbcTemplate.update("delete from game_state_snapshot where session_id = ?", session.getId());

        GameState recovered = gamePersistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 2).gameState();

        assertThat(recovered).isEqualTo(nextState);
        assertThat(recovered.playerCharacter().stats()).isEqualTo(CharacterStats.defaults());
        assertThat(recovered.storyMemory().recentTurns().getLast().playerAction()).isEqualTo("진행");
    }

    private String legacyStateJson() throws Exception {
        ClassPathResource resource = new ClassPathResource(LEGACY_FIXTURE);
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
