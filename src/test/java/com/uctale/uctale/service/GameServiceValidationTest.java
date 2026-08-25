package com.uctale.uctale.service;

import tools.jackson.databind.ObjectMapper;
import com.uctale.uctale.application.game.ChoiceCodec;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.ImagePromptComposer;
import com.uctale.uctale.application.image.ImageGenerator;
import com.uctale.uctale.application.narrative.InvalidNarrativeResponseException;
import com.uctale.uctale.application.narrative.NarrativeGenerator;
import com.uctale.uctale.application.narrative.NarrativeTurn;
import com.uctale.uctale.dto.GameInitRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class GameServiceValidationTest {

    @Mock private NarrativeGenerator narrativeGenerator;
    @Mock private ImageGenerator imageGenerator;
    @Mock private GamePersistenceService gamePersistenceService;

    private GameService gameService;

    @BeforeEach
    void setUp() {
        gameService = new GameService(
                narrativeGenerator,
                imageGenerator,
                gamePersistenceService,
                new ChoiceCodec(new ObjectMapper()),
                new ImagePromptComposer()
        );
    }

    @Test
    @DisplayName("DB user_choice 한계를 넘는 provider 선택지는 이미지 생성과 저장 전에 거부한다")
    void initGame_RejectsOversizedProviderChoiceBeforeSideEffects() {
        GameInitRequest request = new GameInitRequest("세계관", "캐릭터");
        NarrativeTurn invalidTurn = new NarrativeTurn(
                "제목",
                "본문",
                List.of(new NarrativeTurn.Choice(1, "가".repeat(256))),
                new NarrativeTurn.VisualAssets("street", List.of(), List.of())
        );
        given(narrativeGenerator.createOpening("세계관", "캐릭터")).willReturn(invalidTurn);

        assertThatThrownBy(() -> gameService.initGame(request))
                .isInstanceOf(InvalidNarrativeResponseException.class);

        verify(imageGenerator, never()).createPublicUrl(any(), any());
        verify(gamePersistenceService, never()).saveOpening(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("중복 선택지 ID가 있는 provider 응답은 저장 전에 거부한다")
    void initGame_RejectsDuplicateProviderChoiceIds() {
        GameInitRequest request = new GameInitRequest("세계관", "캐릭터");
        NarrativeTurn invalidTurn = new NarrativeTurn(
                "제목",
                "본문",
                List.of(
                        new NarrativeTurn.Choice(1, "간다"),
                        new NarrativeTurn.Choice(1, "멈춘다")
                ),
                new NarrativeTurn.VisualAssets("", List.of(), List.of())
        );
        given(narrativeGenerator.createOpening("세계관", "캐릭터")).willReturn(invalidTurn);

        assertThatThrownBy(() -> gameService.initGame(request))
                .isInstanceOf(InvalidNarrativeResponseException.class);

        verify(gamePersistenceService, never()).saveOpening(any(), any(), any(), any(), any());
    }
}
