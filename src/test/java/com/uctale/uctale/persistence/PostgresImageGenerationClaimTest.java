package com.uctale.uctale.persistence;

import com.uctale.uctale.application.game.ChoiceCodec;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.image.ImageAssetService;
import com.uctale.uctale.dto.GameChoice;
import com.uctale.uctale.repository.ImageAssetRepository;
import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PostgresImageGenerationClaimTest extends PostgresIntegrationTestSupport {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String ASSET_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired private GamePersistenceService persistenceService;
    @Autowired private ChoiceCodec choiceCodec;
    @Autowired private ImageAssetRepository imageAssetRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                truncate table game_turn_reservation, game_mutation_request, image_asset,
                    game_state_snapshot, game_log, game_session restart identity
                """);
    }

    @Test
    @DisplayName("generation lease takeover 뒤 stale owner는 이미지 저장을 commit할 수 없다")
    void staleGenerationOwner_CannotCommitAfterLeaseTakeover() {
        persistenceService.saveOpening(
                OWNER_KEY,
                "세계",
                "인물",
                "첫 이야기",
                choiceCodec.serialize(List.of(new GameChoice(1, "계속한다"))),
                new ImageAssetService.AssetReference(
                        ASSET_ID,
                        "/api/game/image-assets/" + ASSET_ID,
                        "prompt",
                        "16:9",
                        "flux",
                        768,
                        432,
                        123,
                        true,
                        "uctale-charcoal-v3"
                )
        );

        LocalDateTime now = LocalDateTime.now();
        assertThat(imageAssetRepository.claimGeneration(
                ASSET_ID, "owner-a", now, now.plusSeconds(30)
        )).isEqualTo(1);
        assertThat(imageAssetRepository.claimGeneration(
                ASSET_ID, "owner-b", now, now.plusSeconds(30)
        )).isZero();

        jdbcTemplate.update(
                "update image_asset set generation_lease_expires_at = ? where id = ?",
                now.minusSeconds(1),
                ASSET_ID
        );

        assertThat(imageAssetRepository.claimGeneration(
                ASSET_ID, "owner-b", now, now.plusSeconds(30)
        )).isEqualTo(1);
        assertThat(imageAssetRepository.storeGeneratedImageIfOwner(
                ASSET_ID, "owner-a", MediaType.IMAGE_PNG.toString(), new byte[]{1}, now
        )).isZero();
        assertThat(imageAssetRepository.storeGeneratedImageIfOwner(
                ASSET_ID, "owner-b", MediaType.IMAGE_PNG.toString(), new byte[]{2, 3}, now
        )).isEqualTo(1);

        var stored = imageAssetRepository.findById(ASSET_ID).orElseThrow();
        assertThat(stored.generated()).isTrue();
        assertThat(stored.getImageBytes()).containsExactly(2, 3);
        assertThat(stored.getGenerationOwner()).isNull();
        assertThat(stored.getGenerationLeaseExpiresAt()).isNull();
    }
}
