package com.uctale.uctale.application.game;

import com.uctale.uctale.application.narrative.NarrativeTurn;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ImagePromptComposer {

    public String compose(NarrativeTurn.VisualAssets assets) {
        if (assets == null) {
            return null;
        }

        List<String> prompts = new ArrayList<>();
        if (assets.characters() != null) {
            prompts.addAll(assets.characters().stream().filter(value -> value != null && !value.isBlank()).toList());
        }
        if (assets.assets() != null) {
            prompts.addAll(assets.assets().stream().filter(value -> value != null && !value.isBlank()).toList());
        }
        if (assets.background() != null && !assets.background().isBlank()) {
            prompts.add(assets.background());
        }

        return prompts.isEmpty() ? null : String.join(", ", prompts);
    }
}
