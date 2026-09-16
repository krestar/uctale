package com.uctale.uctale.application.narrative;

import com.uctale.uctale.domain.game.GameTurn;
import com.uctale.uctale.domain.game.StorySummary;

import java.util.List;

public interface StoryMemorySummarizer {
    StoryMemorySummaryDraft summarize(StorySummary previousSummary, List<GameTurn> sourceTurns, int stateVersion);
}
