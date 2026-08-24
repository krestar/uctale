package com.uctale.uctale.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uctale.uctale.application.game.ChoiceCodec;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.ImagePromptComposer;
import com.uctale.uctale.application.game.TurnConflictException;
import com.uctale.uctale.application.image.ImageGenerator;
import com.uctale.uctale.application.narrative.NarrativeGenerator;
import com.uctale.uctale.application.narrative.NarrativeTurn;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.dto.GameChoice;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import com.uctale.uctale.dto.GameResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock private NarrativeGenerator narrativeGenerator;
    @Mock private ImageGenerator imageGenerator;
    @Mock private GamePersistenceService gamePersistenceService;

    private ChoiceCodec choiceCodec;
    private GameService gameService;

    @BeforeEach
    void setUp() {
        choiceCodec = new ChoiceCodec(new ObjectMapper());
        gameService = new GameService(
                narrativeGenerator,
                imageGenerator,
                gamePersistenceService,
                choiceCodec,
                new ImagePromptComposer()
        );
    }

    @Test
    @DisplayName("게임 초기화 응답은 첫 번째 턴을 반환한다")
    void initGame_ReturnsFirstTurn() {
        GameInitRequest request = new GameInitRequest("좀비 아포칼립스", "김대리");
        NarrativeTurn opening = new NarrativeTurn(
                "첫날 밤",
                "오프닝 스토리",
                List.of(new NarrativeTurn.Choice(1, "도망간다")),
                new NarrativeTurn.VisualAssets("dark street", List.of("zombie"), List.of())
        );
        GameSession session = new GameSession(request.worldSetting(), request.characterSetting());
        ReflectionTestUtils.setField(session, "id", 42L);

        given(narrativeGenerator.createOpening("좀비 아포칼립스", "김대리")).willReturn(opening);
        given(imageGenerator.createPublicUrl("zombie, dark street", "16:9"))
                .willReturn("/api/game/image?prompt=test&aspectRatio=16%3A9");
        given(gamePersistenceService.saveOpening(any(), any(), any(), any(), any())).willReturn(session);

        GameResponse response = gameService.initGame(request);

        assertThat(response.sessionId()).isEqualTo(42L);
        assertThat(response.turnNumber()).isEqualTo(1);
        assertThat(response.choices()).extracting(GameChoice::text).containsExactly("도망간다");
    }

    @Test
    @DisplayName("게임 진행은 기대 턴을 검증하고 다음 턴을 저장한다")
    void progressGame_UsesExpectedTurn() {
        String choicesJson = choiceCodec.serialize(List.of(new GameChoice(1, "문을 잠근다")));
        GamePersistenceService.LoadedTurn loadedTurn = loadedTurn(choicesJson);
        NarrativeTurn nextTurn = new NarrativeTurn(
                "다음 장면",
                "다음 스토리",
                List.of(new NarrativeTurn.Choice(1, "기다린다")),
                new NarrativeTurn.VisualAssets("", List.of(), List.of())
        );

        given(gamePersistenceService.loadLatestTurn(42L, 1)).willReturn(loadedTurn);
        given(narrativeGenerator.createNextTurn(
                "좀비 아포칼립스", "김대리", "직전 스토리", "문을 잠근다"
        )).willReturn(nextTurn);
        given(gamePersistenceService.saveNextTurn(
                42L,
                1,
                "문을 잠근다",
                "다음 스토리",
                "[{\"id\":1,\"text\":\"기다린다\"}]",
                "/api/game/image?prompt=old&aspectRatio=16%3A9"
        )).willReturn(2);

        GameResponse response = gameService.progressGame(new GameProgressRequest(42L, 1, 1));

        assertThat(response.turnNumber()).isEqualTo(2);
        assertThat(response.storyText()).isEqualTo("다음 스토리");
        verify(gamePersistenceService).saveNextTurn(
                42L,
                1,
                "문을 잠근다",
                "다음 스토리",
                "[{\"id\":1,\"text\":\"기다린다\"}]",
                "/api/game/image?prompt=old&aspectRatio=16%3A9"
        );
    }

    @Test
    @DisplayName("오래된 턴 요청은 내러티브 생성 전에 거부한다")
    void progressGame_RejectsStaleTurnBeforeNarrativeCall() {
        given(gamePersistenceService.loadLatestTurn(42L, 1))
                .willThrow(new TurnConflictException("이미 처리되었거나 오래된 턴 요청입니다."));

        assertThatThrownBy(() -> gameService.progressGame(new GameProgressRequest(42L, 1, 1)))
                .isInstanceOf(TurnConflictException.class);

        verify(narrativeGenerator, never()).createNextTurn(any(), any(), any(), any());
        verify(gamePersistenceService, never()).saveNextTurn(any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("AI 실패 시 다음 턴을 저장하지 않는다")
    void progressGame_DoesNotPersistWhenNarrativeFails() {
        String choicesJson = choiceCodec.serialize(List.of(new GameChoice(1, "문을 잠근다")));
        given(gamePersistenceService.loadLatestTurn(42L, 1)).willReturn(loadedTurn(choicesJson));
        given(narrativeGenerator.createNextTurn(
                "좀비 아포칼립스", "김대리", "직전 스토리", "문을 잠근다"
        )).willThrow(new RuntimeException("AI 호출 실패"));

        assertThatThrownBy(() -> gameService.progressGame(new GameProgressRequest(42L, 1, 1)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("AI 호출 실패");

        verify(gamePersistenceService, never()).saveNextTurn(any(), anyInt(), any(), any(), any(), any());
    }

    private GamePersistenceService.LoadedTurn loadedTurn(String choicesJson) {
        return new GamePersistenceService.LoadedTurn(
                42L,
                1,
                "좀비 아포칼립스",
                "김대리",
                "직전 스토리",
                choicesJson,
                "/api/game/image?prompt=old&aspectRatio=16%3A9"
        );
    }
}
