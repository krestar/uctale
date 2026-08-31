package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.GameLog;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.StateTransition;
import com.uctale.uctale.repository.GameLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class GameNarrativeLinkPersistenceTest {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Autowired private GamePersistenceService gamePersistenceService;
    @Autowired private GameLogRepository gameLogRepository;

    @Test
    @DisplayName("새 progress GameLog는 canonical result와 생성 story 연결 ID를 함께 기록한다")
    void saveNextTurn_PersistsCanonicalResultAndGeneratedStoryLink() {
        GameSession session = gamePersistenceService.saveOpening(
                OWNER_KEY, "세계관", "캐릭터", "오프닝", "[]", null
        );
        GameState previous = gamePersistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 1).gameState();
        GameState next = previous.advance("문을 잠근다", "문이 잠겼다.");
        GameTurnCommit commit = new GameTurnCommit(
                1,
                7,
                "문을 잠근다",
                new StateTransition(previous, next),
                "문이 잠겼다.",
                "[]",
                "game-result:%d:2:100".formatted(session.getId()),
                "story:11111111-1111-1111-1111-111111111111",
                null
        );

        gamePersistenceService.saveNextTurn(OWNER_KEY, session.getId(), commit);

        List<GameLog> logs = gameLogRepository.findByGameSessionOrderByTurnNumberAsc(session);
        assertThat(logs).hasSize(2);
        assertThat(logs.getFirst().getCanonicalResultId()).isNull();
        assertThat(logs.getFirst().getGeneratedStoryId()).isNull();
        assertThat(logs.getLast().getCanonicalResultId())
                .isEqualTo("game-result:%d:2:100".formatted(session.getId()));
        assertThat(logs.getLast().getGeneratedStoryId())
                .isEqualTo("story:11111111-1111-1111-1111-111111111111");
    }

    @Test
    @DisplayName("narrative linkage는 한쪽 ID만 있는 불완전 commit을 거절한다")
    void gameTurnCommit_RejectsPartialNarrativeLink() {
        GameState previous = GameState.initial("세계관", "캐릭터", "오프닝");
        GameState next = previous.advance("간다", "도착했다.");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new GameTurnCommit(
                1, 1, "간다", new StateTransition(previous, next), "도착했다.", "[]",
                "game-result:42:2:100", null, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("함께 기록");
    }
}
