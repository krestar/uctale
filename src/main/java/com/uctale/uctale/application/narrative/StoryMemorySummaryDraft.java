package com.uctale.uctale.application.narrative;

import java.util.List;

public record StoryMemorySummaryDraft(
        int sourceFromTurn,
        int sourceToTurn,
        int stateVersion,
        String text,
        List<String> referencedCanonicalKeys
) {
    public StoryMemorySummaryDraft {
        text = text == null ? "" : text.trim();
        referencedCanonicalKeys = referencedCanonicalKeys == null ? List.of() : List.copyOf(referencedCanonicalKeys);
    }
}
