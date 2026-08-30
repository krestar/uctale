package com.uctale.uctale.service;

import tools.jackson.databind.ObjectMapper;
import com.uctale.uctale.application.cost.CostRateLimitPolicy;
import com.uctale.uctale.application.cost.CostRateLimiter;
import com.uctale.uctale.application.cost.ProviderCallTelemetry;
import com.uctale.uctale.application.game.ChoiceCodec;
import com.uctale.uctale.application.game.GameMutationFingerprint;
import com.uctale.uctale.application.game.GameMutationRequestService;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.ImagePromptComposer;
import com.uctale.uctale.application.image.ImageAssetService;
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

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameServiceValidationTest {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Mock private NarrativeGenerator narrativeGenerator;
    @Mock private ImageAssetService imageAssetService;
    @Mock private GamePersistenceService gamePersistenceService;
    @Mock private GameMutationRequestService mutationRequestService;

    private GameService gameService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.systemUTC();
        given(mutationRequestService.begin(anyString(), anyString(), anyString(), any(), any(), anyString()))
                .willReturn(new GameMutationRequestService.BeginResult(100L, false, null, null, null));
        gameService = new GameService(
                narrativeGenerator,
                imageAssetService,
                gamePersistenceService,
                new ChoiceCodec(new ObjectMapper()),
                new ImagePromptComposer(),
                new CostRateLimiter(new CostRateLimitPolicy(1_000, 1_000, 60), clock),
                new ProviderCallTelemetry(clock, event -> {}),
                new GameMutationFingerprint(),
                mutationRequestService
        );
    }

    @Test
    @DisplayName("DB user_choice 한계를 넘는 provider 선택지는 asset 발급과 저장 전에 거부한다")
    void initGame_RejectsOversizedProviderChoiceBeforeSideEffects() {
        GameInitRequest request = new GameInitRequest("세계관", "캐릭터");
        NarrativeTurn invalidTurn = new NarrativeTurn(
                "제목", "본문", List.of(new NarrativeTurn.Choice(1, "가".repeat(256))),
                new NarrativeTurn.VisualAssets("street", List.of(), List.of())
        );
        given(narrativeGenerator.createOpening("세계관", "캐릭터")).willReturn(invalidTurn);

        assertThatThrownBy(() -> gameService.initGame(OWNER_KEY, request))
                .isInstanceOf(InvalidNarrativeResponseException.class);

        verify(imageAssetService, never()).issue(any(), any());
        verify(gamePersistenceService, never()).saveOpening(any(), any(), any(), any(), any(), any(), any(), any());
        verify(mutationRequestService).markFailed(100L);
    }

    @Test
    @DisplayName("중복 선택지 ID가 있는 provider 응답은 asset 발급과 저장 전에 거부한다")
    void initGame_RejectsDuplicateProviderChoiceIds() {
        GameInitRequest request = new GameInitRequest("세계관", "캐릭터");
        NarrativeTurn invalidTurn = new NarrativeTurn(
                "제목", "본문",
                List.of(new NarrativeTurn.Choice(1, "간다"), new NarrativeTurn.Choice(1, "멈춘다")),
                new NarrativeTurn.VisualAssets("", List.of(), List.of())
        );
        given(narrativeGenerator.createOpening("세계관", "캐릭터")).willReturn(invalidTurn);

        assertThatThrownBy(() -> gameService.initGame(OWNER_KEY, request))
                .isInstanceOf(InvalidNarrativeResponseException.class);

        verify(imageAssetService, never()).issue(any(), any());
        verify(gamePersistenceService, never()).saveOpening(any(), any(), any(), any(), any(), any(), any(), any());
        verify(mutationRequestService).markFailed(100L);
    }
}
