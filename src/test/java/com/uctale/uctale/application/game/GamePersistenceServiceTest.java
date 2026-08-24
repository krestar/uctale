package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.repository.GameLogRepository;
import com.uctale.uctale.repository.GameSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class GamePersistenceServiceTest {

    @Autowired private GamePersistenceService gamePersistenceService;
    @Autowired private GameSessionRepository gameSessionRepository;
    @Autowired private GameLogRepository gameLogRepository;

    @Test
    @DisplayName("턴 저장 후 동일 expectedTurn 재요청은 거부한다")
    void saveNextTurn_RejectsStaleRetry() {
        GameSession session = gamePersistenceService.saveOpening(
                "세계관",
                "캐릭터",
                "첫 이야기",
                "[{\"id\":1,\"text\":\"진행\"}]",
                null
        );

        int savedTurn = gamePersistenceService.saveNextTurn(
                session.getId(),
                1,
                "진행",
                "두 번째 이야기",
                "[{\"id\":1,\"text\":\"계속\"}]",
                null
        );

        assertThat(savedTurn).isEqualTo(2);
        assertThat(gameLogRepository.count()).isEqualTo(2);
        assertThat(gameSessionRepository.findById(session.getId()).orElseThrow().getCurrentTurn()).isEqualTo(2);

        assertThatThrownBy(() -> gamePersistenceService.loadLatestTurn(session.getId(), 1))
                .isInstanceOf(TurnConflictException.class);
    }

    @Test
    @DisplayName("최신 턴 snapshot은 세션과 로그 상태가 일치한다")
    void loadLatestTurn_ReturnsConsistentSnapshot() {
        GameSession session = gamePersistenceService.saveOpening(
                "세계관",
                "캐릭터",
                "첫 이야기",
                "[]",
                "/image"
        );

        GamePersistenceService.LoadedTurn loaded = gamePersistenceService.loadLatestTurn(session.getId(), 1);

        assertThat(loaded.sessionId()).isEqualTo(session.getId());
        assertThat(loaded.turnNumber()).isEqualTo(1);
        assertThat(loaded.storyText()).isEqualTo("첫 이야기");
        assertThat(loaded.imageUrl()).isEqualTo("/image");
    }
}
