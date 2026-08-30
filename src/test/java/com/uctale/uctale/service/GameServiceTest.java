package com.uctale.uctale.service;

import tools.jackson.databind.ObjectMapper;
import com.uctale.uctale.application.cost.CostRateLimitPolicy;
import com.uctale.uctale.application.cost.CostRateLimiter;
import com.uctale.uctale.application.cost.ProviderCallTelemetry;
import com.uctale.uctale.application.game.ChoiceCodec;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.ImagePromptComposer;
import com.uctale.uctale.application.game.TurnConflictException;
import com.uctale.uctale.application.image.ImageAssetService;
import com.uctale.uctale.application.narrative.NarrativeContext;
import com.uctale.uctale.application.narrative.NarrativeGenerator;
import com.uctale.uctale.application.narrative.NarrativeTurn;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.dto.GameChoice;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import com.uctale.uctale.dto.GameResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Mock private NarrativeGenerator narrativeGenerator;
    @Mock private ImageAssetService imageAssetService;
    @Mock private GamePersistenceService gamePersistenceService;

    private ChoiceCodec choiceCodec;
    private GameService gameService;

    @BeforeEach
    void setUp() {
        choiceCodec = new ChoiceCodec(new ObjectMapper());
        Clock clock = Clock.systemUTC();
        gameService = new GameService(
                narrativeGenerator,
                imageAssetService,
                gamePersistenceService,
                choiceCodec,
                new ImagePromptComposer(),
                new CostRateLimiter(new CostRateLimitPolicy(1_000, 1_000, 60), clock),
                new ProviderCallTelemetry(clock, event -> {})
        );
    }

    @Test
    @DisplayName("게임 초기화는 서버 발급 image asset을 owner 세션 저장에 전달한다")
    void initGame_ReturnsFirstTurn() {
        GameInitRequest request = new GameInitRequest("좀비 아포칼립스", "김대리");
        NarrativeTurn opening = new NarrativeTurn(
                "첫날 밤", "오프닝 스토리",
                List.of(new NarrativeTurn.Choice(1, "도망간다")),
                new NarrativeTurn.VisualAssets("dark street", List.of("zombie"), List.of())
        );
        GameSession session = new GameSession(OWNER_KEY, request.worldSetting(), request.characterSetting());
        ReflectionTestUtils.setField(session, "id", 42L);
        ImageAssetService.AssetReference asset = new ImageAssetService.AssetReference(
                "asset-id", "/api/game/image-assets/asset-id", "zombie, dark street", "16:9"
        );

        given(narrativeGenerator.createOpening("좀비 아포칼립스", "김대리")).willReturn(opening);
        given(imageAssetService.issue("zombie, dark street", "16:9")).willReturn(asset);
        given(gamePersistenceService.saveOpening(eq(OWNER_KEY), any(), any(), any(), any(), eq(asset))).willReturn(session);

        GameResponse response = gameService.initGame(OWNER_KEY, request);

        assertThat(response.sessionId()).isEqualTo(42L);
        assertThat(response.turnNumber()).isEqualTo(1);
        assertThat(response.mainImageUrl()).isEqualTo("/api/game/image-assets/asset-id");
        verify(gamePersistenceService).saveOpening(eq(OWNER_KEY), any(), any(), any(), any(), eq(asset));
    }

    @Test
    @DisplayName("시각적 변화가 없으면 이전 image asset을 재사용한다")
    void progressGame_UsesCanonicalGameState() {
        String choicesJson = choiceCodec.serialize(List.of(new GameChoice(1, "문을 잠근다")));
        GamePersistenceService.LoadedTurn loadedTurn = loadedTurn(choicesJson);
        NarrativeTurn nextTurn = new NarrativeTurn(
                "다음 장면", "다음 스토리",
                List.of(new NarrativeTurn.Choice(1, "기다린다")),
                new NarrativeTurn.VisualAssets("", List.of(), List.of())
        );

        given(gamePersistenceService.loadLatestTurn(OWNER_KEY, 42L, 1)).willReturn(loadedTurn);
        given(narrativeGenerator.createNextTurn(any(NarrativeContext.class))).willReturn(nextTurn);
        given(gamePersistenceService.saveNextTurn(
                OWNER_KEY, 42L, 1, "문을 잠근다", "다음 스토리",
                "[{\"id\":1,\"text\":\"기다린다\"}]", null
        )).willReturn(2);

        GameResponse response = gameService.progressGame(OWNER_KEY, new GameProgressRequest(42L, 1, 1));

        assertThat(response.turnNumber()).isEqualTo(2);
        assertThat(response.mainImageUrl()).isEqualTo("/api/game/image-assets/old-asset");
        ArgumentCaptor<NarrativeContext> contextCaptor = ArgumentCaptor.forClass(NarrativeContext.class);
        verify(narrativeGenerator).createNextTurn(contextCaptor.capture());
        assertThat(contextCaptor.getValue().playerAction()).isEqualTo("문을 잠근다");
        verify(imageAssetService, never()).issue(any(), any());
        verify(gamePersistenceService).saveNextTurn(eq(OWNER_KEY), eq(42L), eq(1), any(), any(), any(), eq(null));
    }

    @Test
    @DisplayName("소유권 또는 오래된 턴 거부는 내러티브와 asset 발급 전에 발생한다")
    void progressGame_RejectsBeforeNarrativeCall() {
        given(gamePersistenceService.loadLatestTurn(OWNER_KEY, 42L, 1))
                .willThrow(new TurnConflictException("이미 처리되었거나 오래된 턴 요청입니다."));

        assertThatThrownBy(() -> gameService.progressGame(OWNER_KEY, new GameProgressRequest(42L, 1, 1)))
                .isInstanceOf(TurnConflictException.class);

        verify(narrativeGenerator, never()).createNextTurn(any(NarrativeContext.class));
        verify(imageAssetService, never()).issue(any(), any());
        verify(gamePersistenceService, never()).saveNextTurn(any(), any(), anyInt(), any(), any(), any(), any());
    }

    private GamePersistenceService.LoadedTurn loadedTurn(String choicesJson) {
        return new GamePersistenceService.LoadedTurn(
                42L, 1, "좀비 아포칼립스", "김대리", "직전 스토리", choicesJson,
                "/api/game/image-assets/old-asset",
                GameState.initial("좀비 아포칼립스", "김대리", "직전 스토리")
        );
    }
}
