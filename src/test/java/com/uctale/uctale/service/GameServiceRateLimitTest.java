package com.uctale.uctale.service;

import tools.jackson.databind.ObjectMapper;
import com.uctale.uctale.application.cost.CostOperation;
import com.uctale.uctale.application.cost.CostRateLimitPolicy;
import com.uctale.uctale.application.cost.CostRateLimiter;
import com.uctale.uctale.application.cost.CostRequestContext;
import com.uctale.uctale.application.cost.ProviderCallTelemetry;
import com.uctale.uctale.application.cost.RateLimitExceededException;
import com.uctale.uctale.application.game.ChoiceCodec;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.ImagePromptComposer;
import com.uctale.uctale.application.game.InvalidChoiceException;
import com.uctale.uctale.application.image.ImageAssetService;
import com.uctale.uctale.application.narrative.NarrativeGenerator;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameServiceRateLimitTest {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Mock private NarrativeGenerator narrativeGenerator;
    @Mock private ImageAssetService imageAssetService;
    @Mock private GamePersistenceService gamePersistenceService;

    @Test
    @DisplayName("Narrative quota 초과는 Gemini 호출 전에 거부한다")
    void narrativeLimit_RejectsBeforeProvider() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-30T06:00:00Z"), ZoneOffset.UTC);
        CostRateLimiter limiter = new CostRateLimiter(new CostRateLimitPolicy(1, 10, 60), clock);
        ProviderCallTelemetry telemetry = new ProviderCallTelemetry(clock, event -> {});
        GameService service = new GameService(
                narrativeGenerator,
                imageAssetService,
                gamePersistenceService,
                new ChoiceCodec(new ObjectMapper()),
                new ImagePromptComposer(),
                limiter,
                telemetry
        );
        CostRequestContext context = new CostRequestContext("request-1", OWNER_KEY, "1.2.3.4", null, 1, null);
        limiter.check(CostOperation.NARRATIVE, context);

        assertThatThrownBy(() -> service.initGame(context, new GameInitRequest("세계관", "캐릭터")))
                .isInstanceOf(RateLimitExceededException.class);

        verify(narrativeGenerator, never()).createOpening(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(imageAssetService, never()).issue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("현재 턴에 없는 선택지는 Narrative quota를 소비하지 않는다")
    void invalidChoice_DoesNotConsumeNarrativeQuota() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-30T06:00:00Z"), ZoneOffset.UTC);
        CostRateLimiter limiter = new CostRateLimiter(new CostRateLimitPolicy(1, 10, 60), clock);
        ProviderCallTelemetry telemetry = new ProviderCallTelemetry(clock, event -> {});
        ChoiceCodec choiceCodec = new ChoiceCodec(new ObjectMapper());
        GameService service = new GameService(
                narrativeGenerator,
                imageAssetService,
                gamePersistenceService,
                choiceCodec,
                new ImagePromptComposer(),
                limiter,
                telemetry
        );
        GameState gameState = GameState.initial("세계관", "캐릭터", "첫 이야기");
        String choicesJson = choiceCodec.serialize(java.util.List.of(new com.uctale.uctale.dto.GameChoice(1, "유효한 선택")));
        when(gamePersistenceService.loadLatestTurn(OWNER_KEY, 10L, 1)).thenReturn(
                new GamePersistenceService.LoadedTurn(
                        10L, 1, "세계관", "캐릭터", "첫 이야기", choicesJson, "/image", gameState
                )
        );
        CostRequestContext context = new CostRequestContext("request-1", OWNER_KEY, "1.2.3.4", 10L, 2, null);

        assertThatThrownBy(() -> service.progressGame(context, new GameProgressRequest(10L, 999, 1)))
                .isInstanceOf(InvalidChoiceException.class);

        assertThatCode(() -> limiter.check(CostOperation.NARRATIVE, context)).doesNotThrowAnyException();
        verify(narrativeGenerator, never()).createNextTurn(org.mockito.ArgumentMatchers.any());
    }
}
