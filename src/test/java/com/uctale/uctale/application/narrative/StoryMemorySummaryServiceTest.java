package com.uctale.uctale.application.narrative;

import com.uctale.uctale.application.cost.CostRateLimitPolicy;
import com.uctale.uctale.application.cost.CostRateLimiter;
import com.uctale.uctale.application.cost.CostRequestContext;
import com.uctale.uctale.application.cost.ProviderCallEvent;
import com.uctale.uctale.application.cost.ProviderCallTelemetry;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.StateTransition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class StoryMemorySummaryServiceTest {

    @Test
    @DisplayName("state-owned canonical fact를 참조한 summary는 bounded retry 후 버리고 원래 transition을 보존한다")
    void contradictorySummary_PreservesCanonicalTransition() {
        GameState previous = longState();
        GameState next = previous.advance("마지막 행동", "마지막 장면");
        StateTransition transition = new StateTransition(previous, next);
        AtomicInteger attempts = new AtomicInteger();
        StoryMemorySummarizer summarizer = (previousSummary, sourceTurns, stateVersion) -> {
            attempts.incrementAndGet();
            return new StoryMemorySummaryDraft(
                    sourceTurns.getFirst().turnNumber(),
                    sourceTurns.getLast().turnNumber(),
                    stateVersion,
                    "HP가 999로 회복되었다.",
                    List.of("player.vitals.hp")
            );
        };
        StoryMemorySummaryService service = service(summarizer);

        StateTransition result = service.compactBestEffort(
                transition,
                CostRequestContext.internal("owner", 1L, next.turnNumber())
        );

        assertThat(attempts.get()).isEqualTo(2);
        assertThat(result).isEqualTo(transition);
        assertThat(result.nextState().playerCharacter().vitals()).isEqualTo(next.playerCharacter().vitals());
        assertThat(result.nextState().storyMemory()).isEqualTo(next.storyMemory());
    }

    @Test
    @DisplayName("summary bounded retry는 physical provider invocation마다 독립적인 1 attempt event를 기록한다")
    void summaryRetry_RecordsPhysicalProviderAttemptsOneToOne() {
        GameState previous = longState();
        GameState next = previous.advance("마지막 행동", "마지막 장면");
        StateTransition transition = new StateTransition(previous, next);
        List<ProviderCallEvent> events = new ArrayList<>();
        StoryMemorySummarizer summarizer = (previousSummary, sourceTurns, stateVersion) -> new StoryMemorySummaryDraft(
                sourceTurns.getFirst().turnNumber(),
                sourceTurns.getLast().turnNumber(),
                stateVersion,
                "HP가 999로 회복되었다.",
                List.of("player.vitals.hp")
        );
        StoryMemorySummaryService service = service(summarizer, events);

        StateTransition result = service.compactBestEffort(
                transition,
                CostRequestContext.internal("owner", 1L, next.turnNumber())
        );

        assertThat(result).isEqualTo(transition);
        assertThat(events).hasSize(2).allSatisfy(event -> {
            assertThat(event.operation()).isEqualTo("memory_summary");
            assertThat(event.retryCount()).isZero();
            assertThat(event.attemptCount()).isEqualTo(1);
        });
    }


    @Test
    @DisplayName("summary provider 실패는 원인 분류와 attempt, 연속 terminal failure를 구조화 로그로 남긴다")
    void providerFailure_IsObservableWithoutBreakingTransition(CapturedOutput output) {
        GameState previous = longState();
        GameState next = previous.advance("마지막 행동", "마지막 장면");
        StateTransition transition = new StateTransition(previous, next);
        StoryMemorySummaryService service = service((previousSummary, sourceTurns, stateVersion) -> {
            throw new IllegalStateException("provider unavailable");
        });

        StateTransition result = service.compactBestEffort(
                transition,
                CostRequestContext.internal("owner", 1L, next.turnNumber())
        );

        assertThat(result).isEqualTo(transition);
        assertThat(output).contains(
                "story_memory_summary_failure failureType=PROVIDER attempt=1",
                "story_memory_summary_failure failureType=PROVIDER attempt=2",
                "story_memory_summary_exhausted",
                "consecutiveFailures=1"
        );
    }

    @Test
    @DisplayName("summary validation 실패는 provider 실패와 구분해 attempt를 구조화 로그로 남긴다")
    void validationFailure_IsObservableSeparately(CapturedOutput output) {
        GameState previous = longState();
        GameState next = previous.advance("마지막 행동", "마지막 장면");
        StateTransition transition = new StateTransition(previous, next);
        StoryMemorySummaryService service = service((previousSummary, sourceTurns, stateVersion) ->
                new StoryMemorySummaryDraft(
                        sourceTurns.getFirst().turnNumber(),
                        sourceTurns.getLast().turnNumber(),
                        stateVersion,
                        "HP가 999로 회복되었다.",
                        List.of("player.vitals.hp")
                )
        );

        StateTransition result = service.compactBestEffort(
                transition,
                CostRequestContext.internal("owner", 1L, next.turnNumber())
        );

        assertThat(result).isEqualTo(transition);
        assertThat(output).contains(
                "story_memory_summary_failure failureType=VALIDATION attempt=1",
                "story_memory_summary_failure failureType=VALIDATION attempt=2"
        );
    }

    @Test
    @DisplayName("유효한 summary는 source turn만 compact하고 canonical state는 변경하지 않는다")
    void validSummary_CompactsOnlyNarrativeMemory() {
        GameState previous = longState();
        GameState next = previous.advance("마지막 행동", "마지막 장면");
        StateTransition transition = new StateTransition(previous, next);
        StoryMemorySummarizer summarizer = (previousSummary, sourceTurns, stateVersion) -> new StoryMemorySummaryDraft(
                sourceTurns.getFirst().turnNumber(),
                sourceTurns.getLast().turnNumber(),
                stateVersion,
                "안내인은 북문에서 다시 만나겠다고 약속했다.",
                List.of()
        );
        StoryMemorySummaryService service = service(summarizer);

        StateTransition result = service.compactBestEffort(
                transition,
                CostRequestContext.internal("owner", 1L, next.turnNumber())
        );

        assertThat(result.nextState().storyMemory().rollingSummary().emptySummary()).isFalse();
        assertThat(result.nextState().storyMemory().recentTurns().size())
                .isLessThan(next.storyMemory().recentTurns().size());
        assertThat(result.nextState().playerCharacter()).isEqualTo(next.playerCharacter());
        assertThat(result.nextState().inventory()).isEqualTo(next.inventory());
        assertThat(result.nextState().questState()).isEqualTo(next.questState());
        assertThat(result.nextState().relationshipState()).isEqualTo(next.relationshipState());
    }

    private StoryMemorySummaryService service(StoryMemorySummarizer summarizer) {
        return service(summarizer, new ArrayList<>());
    }

    private StoryMemorySummaryService service(
            StoryMemorySummarizer summarizer,
            List<ProviderCallEvent> events
    ) {
        CostRateLimiter limiter = new CostRateLimiter(new CostRateLimitPolicy(100, 100, 60), Clock.systemUTC());
        ProviderCallTelemetry telemetry = new ProviderCallTelemetry(Clock.systemUTC(), events::add);
        return new StoryMemorySummaryService(summarizer, limiter, telemetry);
    }

    private GameState longState() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        String action = "행동".repeat(500);
        String story = "장면".repeat(2_000);
        for (int turn = 2; turn <= 8; turn++) {
            state = state.advance(action + turn, story + turn);
        }
        return state;
    }
}
