package com.uctale.uctale.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uctale.uctale.application.game.ChoiceCodec;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.ImagePromptComposer;
import com.uctale.uctale.application.image.ImageGenerator;
import com.uctale.uctale.application.narrative.NarrativeGenerator;
import com.uctale.uctale.application.narrative.NarrativeTurn;
import com.uctale.uctale.domain.GameLog;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private NarrativeGenerator narrativeGenerator;

    @Mock
    private ImageGenerator imageGenerator;

    @Mock
    private GamePersistenceService gamePersistenceService;

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
    @DisplayName("게임 초기화는 내러티브와 이미지 포트를 사용하고 세션을 저장한다")
    void initGame_UsesPortsAndPersistsOpening() {
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
        assertThat(response.title()).isEqualTo("첫날 밤");
        assertThat(response.storyText()).isEqualTo("오프닝 스토리");
        assertThat(response.choices()).extracting(GameChoice::text).containsExactly("도망간다");
        assertThat(response.mainImageUrl()).startsWith("/api/game/image?");
        verify(gamePersistenceService).saveOpening(
                "좀비 아포칼립스",
                "김대리",
                "오프닝 스토리",
                "[{\"id\":1,\"text\":\"도망간다\"}]",
                "/api/game/image?prompt=test&aspectRatio=16%3A9"
        );
    }

    @Test
    @DisplayName("게임 진행은 저장된 선택지를 해석해 다음 내러티브를 생성한다")
    void progressGame_UsesStoredChoiceAndPreviousStory() {
        GameSession session = new GameSession("좀비 아포칼립스", "김대리");
        ReflectionTestUtils.setField(session, "id", 42L);
        String choicesJson = choiceCodec.serialize(List.of(new GameChoice(1, "문을 잠근다")));
        GameLog lastLog = new GameLog(
                session,
                1,
                "직전 스토리",
                choicesJson,
                "/api/game/image?prompt=old&aspectRatio=16%3A9"
        );
        NarrativeTurn nextTurn = new NarrativeTurn(
                "다음 장면",
                "다음 스토리",
                List.of(new NarrativeTurn.Choice(1, "기다린다")),
                new NarrativeTurn.VisualAssets("", List.of(), List.of())
        );

        given(gamePersistenceService.loadLatestTurn(42L))
                .willReturn(new GamePersistenceService.LoadedTurn(session, lastLog));
        given(narrativeGenerator.createNextTurn(
                "좀비 아포칼립스",
                "김대리",
                "직전 스토리",
                "문을 잠근다"
        )).willReturn(nextTurn);

        GameResponse response = gameService.progressGame(new GameProgressRequest(42L, 1));

        assertThat(response.sessionId()).isEqualTo(42L);
        assertThat(response.storyText()).isEqualTo("다음 스토리");
        assertThat(response.mainImageUrl()).isEqualTo("/api/game/image?prompt=old&aspectRatio=16%3A9");
        verify(narrativeGenerator).createNextTurn(
                "좀비 아포칼립스",
                "김대리",
                "직전 스토리",
                "문을 잠근다"
        );
        verify(gamePersistenceService).saveNextTurn(
                lastLog,
                "문을 잠근다",
                "다음 스토리",
                "[{\"id\":1,\"text\":\"기다린다\"}]",
                "/api/game/image?prompt=old&aspectRatio=16%3A9"
        );
    }

    @Test
    @DisplayName("존재하지 않는 선택지는 내러티브 생성 전에 거부한다")
    void progressGame_RejectsUnknownChoiceBeforeNarrativeCall() {
        GameSession session = new GameSession("좀비 아포칼립스", "김대리");
        ReflectionTestUtils.setField(session, "id", 42L);
        GameLog lastLog = new GameLog(
                session,
                1,
                "직전 스토리",
                choiceCodec.serialize(List.of(new GameChoice(1, "문을 잠근다"))),
                null
        );
        given(gamePersistenceService.loadLatestTurn(42L))
                .willReturn(new GamePersistenceService.LoadedTurn(session, lastLog));

        assertThatThrownBy(() -> gameService.progressGame(new GameProgressRequest(42L, 999)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 선택지입니다.");

        verify(narrativeGenerator, never()).createNextTurn(any(), any(), any(), any());
        verify(gamePersistenceService, never()).saveNextTurn(any(), any(), any(), any(), any());
    }
}
