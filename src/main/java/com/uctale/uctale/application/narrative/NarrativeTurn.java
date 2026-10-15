package com.uctale.uctale.application.narrative;

import java.util.List;

public record NarrativeTurn(
        String title,
        String storyText,
        List<Choice> choices,
        VisualAssets visualAssets
) {
    public record Choice(int id, String text, String intent, String subjectKey) {
        public Choice(int id, String text) {
            this(id, text, null, null);
        }
    }

    public record VisualAssets(
            String background,
            List<String> characters,
            List<String> assets
    ) {}
}
