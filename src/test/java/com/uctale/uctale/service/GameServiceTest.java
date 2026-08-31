package com.uctale.uctale.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uctale.uctale.ai.NarrativeContext;
import com.uctale.uctale.ai.NarrativeGenerator;
import com.uctale.uctale.ai.NarrativeTurn;
import com.uctale.uctale.application.game.GameMutationFingerprint;
import com.uctale.uctale.application.game.GameMutationRequestService;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.cost.CostOperation;
import com.uctale.uctale.cost.CostRateLimitPolicy;
import com.uctale.uctale.cost.CostRateLimiter;
import com.uctale.uctale.cost.CostRequestContext;
import com.uctale.uctale.cost.ProviderCallTelemetry;
import com.uctale.uctale.dto.GameChoice;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import com.uctale.uctale.dto.GameResponse;
import com.uctale.uctale.image.ImageAssetService;
import com.uctale.uctale.image.ImagePromptComposer;
import com.uctale.uctale.model.GameState;
import com.uctale.uctale.model.GameTurnCommit;
import com.uctale.uctale.util.ChoiceCodec;
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
    @DisplayName("init은 persistence 전에 narrative와 choice를 확정한다")
    void initializeGame_PreparesNarrativeBeforePersistence() {
        NarrativeTurn opening = new NarrativeTurn(
                "오프닝", "스토리",
                List.of(new NarrativeTurn.Choice(1, "간다")),
                new NarrativeTurn.VisualAssets("", List.of(), List.of())
        );
        given(narrativeGenerator.createOpening("세계관", "캐릭터")).willReturn(opening);
        given(gamePersistenceService.saveOpening(any(), any(), any(), any(), any(), any(), any(), any()))
                .willAnswer(invocation -> {
                    var session = new com.uctale.uctale.domain.GameSession(OWNER_KEY, "세계관", "캐릭터");
                    ReflectionTestUtils.setField(session, "id", 42L);
                    return session;
                });

        GameResponse response = gameService.initializeGame(
                OWNER_KEY,
                new GameInitRequest("세계관", "캐릭터")
        );

        assertThat(response.sessionId()).isEqualTo(42L);
        assertThat(response.turnNumber()).isEqualTo(1);
        verify(narrativeGenerator).createOpening("세계관", "캐릭터");
    }

    @Test
    @DisplayName("init replay는 provider와 persistence를 다시 호출하지 않는다")
    void initializeGame_ReplaysCompletedMutation() {
        given(mutationRequestService.begin(anyString(), eq(GameMutationRequestService.INIT), anyString(), any(), any(), anyString()))
                .willReturn(new GameMutationRequestService.BeginResult(100L, true, 42L, 1, "기존 오프닝"));
        given(gamePersistenceService.loadTurnForReplay(OWNER_KEY, 42L, 1))
                .willReturn(new GamePersistenceService.ReplayedTurn(
                        42L, 1, "기존 오프닝", "기존 스토리",
                        choiceCodec.serialize(List.of(new GameChoice(1, "간다"))), null
                ));

        GameResponse response = gameService.initializeGame(
                OWNER_KEY,
                new GameInitRequest("세계관", "캐릭터")
        );

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
    @DisplayName("잘못된 choice는 provider를 호출하지 않는다")
    void progressGame_RejectsInvalidChoiceBeforeProviderCall() {
        given(gamePersistenceService.loadLatestTurn(OWNER_KEY, 42L, 1))
                .willReturn(loadedTurn(choiceCodec.serialize(List.of(new GameChoice(1, "간다")))));

        assertThatThrownBy(() -> gameService.progressGame(OWNER_KEY, new GameProgressRequest(42L, 99, 1)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(narrativeGenerator, never()).createNextTurn(any());
    }

    private GamePersistenceService.LoadedTurn loadedTurn(String choicesJson) {
        GameState state = GameState.initial("세계관", "캐릭터", "첫 장면");
        return new GamePersistenceService.LoadedTurn(
                42L, 1, "첫 장면", choicesJson, "/api/game/image-assets/old-asset", state, 1
        );
    }
}
