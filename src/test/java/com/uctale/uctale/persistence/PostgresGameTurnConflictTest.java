package com.uctale.uctale.persistence;

import com.uctale.uctale.application.game.ChoiceCodec;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.GameTurnCommit;
import com.uctale.uctale.application.game.TurnConflictException;
import com.uctale.uctale.application.image.ImageAssetService;
import com.uctale.uctale.domain.ImageAsset;
import com.uctale.uctale.dto.GameChoice;
import com.uctale.uctale.repository.ImageAssetRepository;
import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PostgresGameTurnConflictTest extends PostgresIntegrationTestSupport {
    private static final String OWNER = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String EXISTING = "44444444-4444-4444-4444-444444444444";
    private static final String NEW_ASSET = "55555555-5555-5555-5555-555555555555";

    @Autowired private GamePersistenceService persistenceService;
    @Autowired private ImageAssetRepository imageAssetRepository;
    @Autowired private ChoiceCodec choiceCodec;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                truncate table game_turn_reservation, game_mutation_request, image_asset,
                    game_state_snapshot, game_log, game_session restart identity
                """);
    }

    @Test
    void uniqueImageTurnConflict_FromRealPostgres23505BecomesTurnConflict() {
        var session = persistenceService.saveOpening(OWNER, "세계", "캐릭터", "첫 장면",
                choiceCodec.serialize(List.of(new GameChoice(1, "진행"))), null);
        var previous = persistenceService.loadLatestTurn(OWNER, session.getId(), 1).gameState();
        imageAssetRepository.saveAndFlush(new ImageAsset(EXISTING, session, 2,
                "이미 점유한 턴", "16:9", "flux", 768, 432, 1, true, "v1"));

        var asset = new ImageAssetService.AssetReference(NEW_ASSET,
                "/api/game/image-assets/" + NEW_ASSET, "다음 장면", "16:9", "flux", 768, 432, 2, true, "v1");
        var commit = new GameTurnCommit(1, 1, "진행", previous, previous.advance("진행", "둘째 장면"),
                "둘째 장면", "[]", asset);

        assertThatThrownBy(() -> persistenceService.saveNextTurn(OWNER, session.getId(), commit))
                .isInstanceOf(TurnConflictException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }
}
