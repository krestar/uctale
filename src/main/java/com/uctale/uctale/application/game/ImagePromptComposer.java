package com.uctale.uctale.application.game;

import com.uctale.uctale.application.narrative.NarrativeTurn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ImagePromptComposer {

    static final String DEFAULT_STYLE_VERSION = "uctale-charcoal-v1";
    private static final int MAX_PROMPT_LENGTH = 1_800;
    private static final Map<String, String> STYLE_PRESETS = Map.of(
            DEFAULT_STYLE_VERSION,
            "rough charcoal sketch, high contrast black and white, gritty paper texture, expressive pencil strokes, no colors, story concept art"
    );
    private static final String ATMOSPHERE = "atmosphere: dramatic storybook scene";
    private static final String COMPOSITION = "composition: clear focal point, readable silhouettes, cinematic depth";

    private final String styleVersion;
    private final String stylePrompt;

    public ImagePromptComposer() {
        this(DEFAULT_STYLE_VERSION);
    }

    @Autowired
    public ImagePromptComposer(@Value("${game.image.style-version:uctale-charcoal-v1}") String styleVersion) {
        String normalized = styleVersion == null ? "" : styleVersion.trim();
        String preset = STYLE_PRESETS.get(normalized);
        if (preset == null) {
            throw new IllegalArgumentException("지원하지 않는 이미지 style version입니다: " + normalized);
        }
        this.styleVersion = normalized;
        this.stylePrompt = preset;
    }

    public String compose(NarrativeTurn.VisualAssets assets) {
        if (assets == null) {
            return null;
        }

        List<String> sections = new ArrayList<>();
        addSection(sections, "subjects", assets.characters());
        addSection(sections, "objects", assets.assets());
        if (assets.background() != null && !assets.background().isBlank()) {
            addSection(sections, "setting", List.of(assets.background()));
        }
        if (sections.isEmpty()) {
            return null;
        }
        return finish(String.join("; ", sections));
    }

    public String composeFallback(String worldSetting) {
        String setting = normalize(worldSetting);
        if (setting == null) {
            setting = "mysterious unknown location";
        }
        return finish("setting: " + setting);
    }

    private void addSection(List<String> sections, String label, List<String> values) {
        List<String> normalized = distinct(values);
        if (!normalized.isEmpty()) {
            sections.add(label + ": " + String.join(", ", normalized));
        }
    }

    private List<String> distinct(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Map<String, String> distinct = new LinkedHashMap<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                distinct.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
            }
        }
        return List.copyOf(distinct.values());
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private String finish(String scenePrompt) {
        String suffix = "; " + ATMOSPHERE + "; " + COMPOSITION
                + "; style[" + styleVersion + "]: " + stylePrompt;
        int sceneBudget = MAX_PROMPT_LENGTH - suffix.length();
        String limitedScene = scenePrompt.length() <= sceneBudget
                ? scenePrompt
                : scenePrompt.substring(0, Math.max(0, sceneBudget)).stripTrailing();
        return limitedScene + suffix;
    }
}
