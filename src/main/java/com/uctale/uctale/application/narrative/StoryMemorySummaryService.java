package com.uctale.uctale.application.narrative;

import com.uctale.uctale.application.cost.CostOperation;
import com.uctale.uctale.application.cost.CostRateLimiter;
import com.uctale.uctale.application.cost.CostRequestContext;
import com.uctale.uctale.application.cost.ProviderCallTelemetry;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.GameTurn;
import com.uctale.uctale.domain.game.StateTransition;
import com.uctale.uctale.domain.game.StoryMemory;
import com.uctale.uctale.domain.game.StoryMemoryFactPolicy;
import com.uctale.uctale.domain.game.StorySummary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class StoryMemorySummaryService {

    private static final int MAX_ATTEMPTS = 2;

    private final StoryMemorySummarizer summarizer;
    private final CostRateLimiter costRateLimiter;
    private final ProviderCallTelemetry telemetry;

    public StoryMemorySummaryService(StoryMemorySummarizer summarizer, CostRateLimiter costRateLimiter,
                                     ProviderCallTelemetry telemetry) {
        this.summarizer = summarizer;
        this.costRateLimiter = costRateLimiter;
        this.telemetry = telemetry;
    }

    public StateTransition compactBestEffort(StateTransition transition, CostRequestContext context) {
        if (transition == null) throw new IllegalArgumentException("StateTransition은 필수입니다.");
        GameState state = transition.nextState();
        StoryMemory memory = state.storyMemory();
        if (StoryMemoryProjectionSelector.estimateRawTurns(memory.recentTurns())
                <= StoryMemoryProjectionSelector.SUMMARY_TRIGGER_TOKEN_BUDGET) {
            return transition;
        }
        List<GameTurn> source = StoryMemoryProjectionSelector.selectSummarySource(memory.recentTurns());
        if (source.isEmpty()) return transition;

        StorySummary previous = memory.rollingSummary();
        int sourceFrom = previous.emptySummary() ? source.getFirst().turnNumber() : previous.sourceFromTurn();
        int sourceTo = source.getLast().turnNumber();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                costRateLimiter.check(CostOperation.NARRATIVE, context);
                StoryMemorySummaryDraft draft = telemetry.observe(
                        "gemini", "memory_summary", context, attempt,
                        () -> summarizer.summarize(previous, source, state.turnNumber())
                );
                StorySummary summary = validate(draft, sourceFrom, sourceTo, state.turnNumber());
                GameState compacted = state.withStoryMemory(memory.compact(summary));
                return new StateTransition(transition.previousState(), compacted);
            } catch (RuntimeException ignored) {
                // Summary는 canonical turn 성공 조건이 아니다. bounded retry 후 이전 memory를 그대로 보존한다.
            }
        }
        return transition;
    }

    private StorySummary validate(StoryMemorySummaryDraft draft, int sourceFrom, int sourceTo, int stateVersion) {
        if (draft == null || draft.sourceFromTurn() != sourceFrom || draft.sourceToTurn() != sourceTo
                || draft.stateVersion() != stateVersion || draft.text().isBlank()) {
            throw new IllegalArgumentException("summary schema가 요청 source range/state version과 일치하지 않습니다.");
        }
        if (StoryMemoryProjectionSelector.estimate(draft.text()) > StoryMemoryProjectionSelector.SUMMARY_TOKEN_BUDGET) {
            throw new IllegalArgumentException("summary가 token budget을 초과했습니다.");
        }
        for (String key : draft.referencedCanonicalKeys()) {
            if (StoryMemoryFactPolicy.isStateOwned(key)) {
                throw new IllegalArgumentException("summary가 canonical GameState 사실을 중복 참조합니다: " + key);
            }
        }
        return new StorySummary(sourceFrom, sourceTo, stateVersion, draft.text());
    }
}
