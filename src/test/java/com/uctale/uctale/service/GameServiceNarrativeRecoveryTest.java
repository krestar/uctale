package com.uctale.uctale.service;

import tools.jackson.databind.ObjectMapper;
import com.uctale.uctale.application.cost.CostRateLimitPolicy;
import com.uctale.uctale.application.cost.CostRateLimiter;
import com.uctale.uctale.application.cost.ProviderCallEvent;
import com.uctale.uctale.application.cost.ProviderCallTelemetry;
import com.uctale.uctale.application.game.ChoiceCodec;
import com.uctale.uctale.application.game.GameMutationFingerprint;
import com.uctale.uctale.application.game.GameMutationRequestService;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.ImagePromptComposer;
import com.uctale.uctale.application.game.TurnProcessor;
import com.uctale.uctale.application.image.ImageAssetService;
import com.uctale.uctale.application.narrative.NarrativeGenerator;
import com.uctale.uctale.application.narrative.NarrativeRecoveryExhaustedException;
import com.uctale.uctale.application.narrative.RecoverableNarrativeResponseException;
import com.uctale.uctale.dto.GameInitRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameServiceNarrativeRecoveryTest {
    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    @Mock private NarrativeGenerator narrativeGenerator;
    @Mock private ImageAssetService imageAssetService;
    @Mock private GamePersistenceService gamePersistenceService;
    @Mock private GameMutationRequestService mutationRequestService;
    private final List<ProviderCallEvent> events = new ArrayList<>();
    private GameService gameService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.systemUTC();
        given(mutationRequestService.begin(anyString(), anyString(), anyString(), any(), any(), anyString())).willReturn(new GameMutationRequestService.BeginResult(100L, false, null, null, null));
        gameService = new GameService(narrativeGenerator, imageAssetService, gamePersistenceService, new ChoiceCodec(new ObjectMapper()), new TurnProcessor(), new ImagePromptComposer(), new CostRateLimiter(new CostRateLimitPolicy(1_000, 1_000, 60), clock), new ProviderCallTelemetry(clock, events::add), new GameMutationFingerprint(), mutationRequestService);
    }

    @Test
    @DisplayName("Gemini response recovery 소진 시 3회 attempt를 기록하고 canonical opening을 저장하지 않는다")
    void initGame_RecoveryExhausted_DoesNotCommit() {
        GameInitRequest request = new GameInitRequest("세계관", "캐릭터");
        given(narrativeGenerator.createOpening("세계관", "캐릭터")).willThrow(new RecoverableNarrativeResponseException("MALFORMED_JSON", "invalid"));
        given(narrativeGenerator.repairOpening("세계관", "캐릭터", "MALFORMED_JSON")).willThrow(new RecoverableNarrativeResponseException("MALFORMED_JSON", "invalid"));
        assertThatThrownBy(() -> gameService.initGame(OWNER_KEY, request)).isInstanceOfSatisfying(NarrativeRecoveryExhaustedException.class, exception -> assertThat(exception.retryCount()).isEqualTo(2));
        verify(narrativeGenerator).createOpening("세계관", "캐릭터");
        verify(narrativeGenerator, times(2)).repairOpening("세계관", "캐릭터", "MALFORMED_JSON");
        verify(imageAssetService, never()).issue(any(), any());
        verify(gamePersistenceService, never()).saveOpening(any(), any(), any(), any(), any(), any(), any(), any());
        verify(mutationRequestService).markFailed(100L);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.provider()).isEqualTo("gemini");
            assertThat(event.outcome()).isEqualTo("FAILURE");
            assertThat(event.retryCount()).isEqualTo(2);
            assertThat(event.attemptCount()).isEqualTo(3);
        });
    }
}
