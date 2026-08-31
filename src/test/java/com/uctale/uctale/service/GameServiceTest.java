package com.uctale.uctale.service;

import tools.jackson.databind.ObjectMapper;
import com.uctale.uctale.application.cost.CostRateLimitPolicy;
import com.uctale.uctale.application.cost.CostRateLimiter;
import com.uctale.uctale.application.cost.ProviderCallTelemetry;
import com.uctale.uctale.application.game.ChoiceCodec;
import com.uctale.uctale.application.game.GameMutationFingerprint;
import com.uctale.uctale.application.game.GameMutationRequestService;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.GameTurnCommit;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Mock private NarrativeGenerator narrativeGenerator;
    @Mock private ImageAssetService imageAssetService;
    @Mock private GamePersistenceService gamePersistenceService;
    @Mock private GameMutationRequestService mutationRequestService;

    private ChoiceCodec choiceCodec;
    private GameService gameService;

    @BeforeEach
    void setUp() {
        choiceCodec = new ChoiceCodec(new ObjectMapper());
        Clock clock = Clock.systemUTC();
        gameService = new GameService(
                narrativeGenerator, imageAssetService, gamePersistenceService, choiceCodec,
                new ImagePromptComposer(),
                new CostRateLimiter(new CostRateLimitPolicy(1_000, 1_000, 60), clock),
                new ProviderCallTelemetry(clock, event -> {}),
                new GameMutationFingerprint(),
                mutationRequestService
        );
        lenient().when(mutationRequestService.begin(anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(new GameMutationRequestService.BeginResult(100L, false, null, null, null, "lease-owner"));
    }

    @Test
    @DisplayName("게임 초기화는 versioned prompt로 서버 발급 image asset을 저장에 전달한다")
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
                "asset-id", "/api/game/image-assets/asset-id", "prompt", "16:9",
                "flux", 1024, 576, 123, true, "uctale-charcoal-v2"
        );

        given(narrativeGenerator.createOpening("좀비 아포칼립스", "김대리")).willReturn(opening);
        given(imageAssetService.issue(any(String.class), eq("16:9"))).willReturn(asset);
        given(gamePersistenceService.saveOpening(
                eq(OWNER_KEY), any(), any(), any(), any(), eq(asset), eq(100L), eq("첫날 밤")
        )).willReturn(session);

        GameResponse response = gameService.initGame(OWNER_KEY, request);

        assertThat(response.sessionId()).isEqualTo(42L);
        assertThat(response.turnNumber()).isEqualTo(1);
        assertThat(response.mainImageUrl()).isEqualTo("/api/game/image-assets/asset-id");
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(imageAssetService).issue(promptCaptor.capture(), eq("16:9"));
        assertThat(promptCaptor.getValue())
                .startsWith("style[uctale-charcoal-v2]")
                .contains("subjects: zombie", "setting: dark street", "final style lock:");
    }

    @Test
    @DisplayName("완료된 init retry는 provider와 새 session 생성 없이 기존 결과를 반환한다")
    void initGame_ReplaysCompletedMutation() {
        given(mutationRequestService.begin(anyString(), eq(GameMutationRequestService.INIT), anyString(), any(), any(), anyString()))
                .willReturn(new GameMutationRequestService.BeginResult(100L, true, 42L, 1, "기존 오프닝"));
        given(gamePersistenceService.loadCommittedTurn(OWNER_KEY, 42L, 1))
                .willReturn(new GamePersistenceService.CommittedTurn(
                        "기존 스토리",
                        choiceCodec.serialize(List.of(new GameChoice(3, "계속한다"))),
                        "/api/game/image-assets/replayed"
                ));

        GameResponse response = gameService.initGame(OWNER_KEY, new GameInitRequest("세계관", "캐릭터"));

        assertThat(response.sessionId()).isEqualTo(42L);
        assertThat(response.turnNumber()).isEqualTo(1);
        assertThat(response.title()).isEqualTo("기존 오프닝");
        verify(narrativeGenerator, never()).createOpening(anyString(), anyString());
        verify(gamePersistenceService, never()).saveOpening(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("progress는 persistence 전에 입력과 다음 canonical state를 확정한다")
    void progressGame_PreparesCommittedStateTransitionBeforePersistence() {
        String choicesJson = choiceCodec.serialize(List.of(new GameChoice(7, "문을 잠근다")));
        GamePersistenceService.LoadedTurn loadedTurn = loadedTurn(choicesJson);
        NarrativeTurn nextTurn = new NarrativeTurn(
                "다음 장면", "다음 스토리",
                List.of(new NarrativeTurn.Choice(1, "기다린다")),
                new NarrativeTurn.VisualAssets("", List.of(), List.of())
        );

        given(gamePersistenceService.loadLatestTurn(OWNER_KEY, 42L, 1)).willReturn(loadedTurn);
        given(narrativeGenerator.createNextTurn(any(NarrativeContext.class))).willReturn(nextTurn);
        given(gamePersistenceService.saveNextTurn(
                eq(OWNER_KEY), eq(42L), any(GameTurnCommit.class), eq(100L), eq("다음 장면"), eq("lease-owner")
        )).willReturn(2);

        GameResponse response = gameService.progressGame(OWNER_KEY, new GameProgressRequest(42L, 7, 1));

        assertThat(response.turnNumber()).isEqualTo(2);
        assertThat(response.mainImageUrl()).isEqualTo("/api/game/image-assets/old-asset");

        ArgumentCaptor<NarrativeContext> contextCaptor = ArgumentCaptor.forClass(NarrativeContext.class);
        verify(narrativeGenerator).createNextTurn(contextCaptor.capture());
        assertThat(contextCaptor.getValue().playerAction()).isEqualTo("문을 잠근다");

        ArgumentCaptor<GameTurnCommit> commitCaptor = ArgumentCaptor.forClass(GameTurnCommit.class);
        verify(gamePersistenceService).saveNextTurn(
                eq(OWNER_KEY), eq(42L), commitCaptor.capture(), eq(100L), eq("다음 장면"), eq("lease-owner")
        );
        GameTurnCommit commit = commitCaptor.getValue();
        assertThat(commit.inputChoiceId()).isEqualTo(7);
        assertThat(commit.inputChoiceText()).isEqualTo("문을 잠근다");
        assertThat(commit.previousStateVersion()).isEqualTo(1);
        assertThat(commit.nextStateVersion()).isEqualTo(2);
        assertThat(commit.nextState().storyMemory().recentTurns()).hasSize(2);
        assertThat(commit.storyText()).isEqualTo("다음 스토리");
        verify(imageAssetService, never()).issue(any(), any());
    }

    @Test
    @DisplayName("완료된 progress retry는 provider와 state mutation 없이 기존 결과를 반환한다")
    void progressGame_ReplaysCompletedMutation() {
        given(mutationRequestService.begin(anyString(), eq(GameMutationRequestService.PROGRESS), anyString(), eq(42L), eq(1), anyString()))
                .willReturn(new GameMutationRequestService.BeginResult(100L, true, 42L, 2, "기존 장면"));
        given(gamePersistenceService.loadCommittedTurn(OWNER_KEY, 42L, 2))
                .willReturn(new GamePersistenceService.CommittedTurn(
                        "기존 스토리",
                        choiceCodec.serialize(List.of(new GameChoice(3, "계속한다"))),
                        "/api/game/image-assets/replayed"
                ));

        GameResponse response = gameService.progressGame(OWNER_KEY, new GameProgressRequest(42L, 7, 1));

        assertThat(response.turnNumber()).isEqualTo(2);
        assertThat(response.title()).isEqualTo("기존 장면");
        assertThat(response.storyText()).isEqualTo("기존 스토리");
        verify(narrativeGenerator, never()).createNextTurn(any());
        verify(gamePersistenceService, never()).loadLatestTurn(anyString(), any(), anyInt());
        verify(gamePersistenceService, never()).saveNextTurn(anyString(), any(), any(GameTurnCommit.class), any(), any());
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
        verify(gamePersistenceService, never()).saveNextTurn(any(), any(), any(GameTurnCommit.class), any(), any());
        verify(mutationRequestService).markFailed(100L, "lease-owner");
    }

    private GamePersistenceService.LoadedTurn loadedTurn(String choicesJson) {
        return new GamePersistenceService.LoadedTurn(
                42L, 1, "좀비 아포칼립스", "김대리", "직전 스토리", choicesJson,
                "/api/game/image-assets/old-asset",
                GameState.initial("좀비 아포칼립스", "김대리", "직전 스토리")
        );
    }
}
