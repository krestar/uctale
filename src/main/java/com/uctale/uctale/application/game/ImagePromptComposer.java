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

    static final String LEGACY_STYLE_VERSION = "uctale-charcoal-v1";
    static final String V2_STYLE_VERSION = "uctale-charcoal-v2";
    static final String DEFAULT_STYLE_VERSION = "uctale-charcoal-v3";
    private static final int MAX_PROMPT_LENGTH = 1_800;

    private static final String V1_STYLE_PROMPT =
            "rough charcoal sketch, high contrast black and white, gritty paper texture, expressive pencil strokes, no colors, story concept art";
    private static final String V1_ATMOSPHERE = "atmosphere: dramatic storybook scene";
    private static final String V1_COMPOSITION = "composition: clear focal point, readable silhouettes, cinematic depth";

    private static final String V2_STYLE_PREFIX =
            "style[uctale-charcoal-v2]: monochrome charcoal and graphite drawing on off-white paper, "
                    + "grayscale only, visible charcoal grain, smudged shading, expressive hand-drawn strokes, "
                    + "no colored pigments or color accents, no watercolor, no oil painting, "
                    + "no digital color painting, no photorealism, no 3D render";
    private static final String V2_ATMOSPHERE =
            "atmosphere: narrative editorial scene with restrained tonal drama";
    private static final String V2_COMPOSITION =
            "composition: clear focal point, readable silhouettes, layered hand-drawn depth";
    private static final String V2_FINAL_LOCK =
            "final style lock: monochrome charcoal and graphite only; render fire, explosions, neon, sunsets, "
                    + "and glowing objects using black, gray, and white tonal values only; no color";

    private static final String V3_STYLE_PREFIX =
            "style[uctale-charcoal-v3]: raw monochrome charcoal/graphite sketch on coarse off-white paper; "
                    + "rough uneven linework, high-contrast black/white structure, grayscale only; coarse paper grain, "
                    + "dry-media texture, dense cross-hatching, scratched graphite, smudged deep shadows, "
                    + "erased/scraped white highlights; imperfect raw concept-sketch finish; no colored pigment/accent, "
                    + "polished/editorial digital illustration, watercolor, oil painting, photorealism, or 3D render";
    private static final String V3_ATMOSPHERE =
            "atmosphere: deep monochrome shadows and tactile charcoal energy";
    private static final String V3_COMPOSITION =
            "composition: readable silhouettes, strong depth, raw hand-drawn layering";
    private static final String V3_FINAL_LOCK =
            "final style lock: raw charcoal/graphite only; preserve fire, explosions, neon, sunsets and glowing objects, "
                    + "but render color-prone subjects in black/gray/white tonal values only; no colored accent";

    private static final Map<String, String> SUPPORTED_STYLES = Map.of(
            LEGACY_STYLE_VERSION, V1_STYLE_PROMPT,
            V2_STYLE_VERSION, V2_STYLE_PREFIX,
            DEFAULT_STYLE_VERSION, V3_STYLE_PREFIX
    );

    private final String styleVersion;

    public ImagePromptComposer() {
        this(DEFAULT_STYLE_VERSION);
    }

    @Autowired
    public ImagePromptComposer(@Value("${game.image.style-version:uctale-charcoal-v3}") String styleVersion) {
        String normalized = styleVersion == null ? "" : styleVersion.trim();
        if (!SUPPORTED_STYLES.containsKey(normalized)) {
            throw new IllegalArgumentException("지원하지 않는 이미지 style version입니다: " + normalized);
        }
        this.styleVersion = normalized;
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
        if (LEGACY_STYLE_VERSION.equals(styleVersion)) {
            return finishLegacyV1(scenePrompt);
        }
        if (V2_STYLE_VERSION.equals(styleVersion)) {
            return finishV2(scenePrompt);
        }
        return finishV3(scenePrompt);
    }

    private String finishLegacyV1(String scenePrompt) {
        String suffix = "; " + V1_ATMOSPHERE + "; " + V1_COMPOSITION
                + "; style[" + LEGACY_STYLE_VERSION + "]: " + V1_STYLE_PROMPT;
        int sceneBudget = MAX_PROMPT_LENGTH - suffix.length();
        return limit(scenePrompt, sceneBudget) + suffix;
    }

    private String finishV2(String scenePrompt) {
        String prefix = V2_STYLE_PREFIX + "; ";
        String suffix = "; " + V2_ATMOSPHERE + "; " + V2_COMPOSITION + "; " + V2_FINAL_LOCK;
        int sceneBudget = MAX_PROMPT_LENGTH - prefix.length() - suffix.length();
        return prefix + limit(scenePrompt, sceneBudget) + suffix;
    }

    private String finishV3(String scenePrompt) {
        String prefix = V3_STYLE_PREFIX + "; ";
        String suffix = "; " + V3_ATMOSPHERE + "; " + V3_COMPOSITION + "; " + V3_FINAL_LOCK;
        int sceneBudget = MAX_PROMPT_LENGTH - prefix.length() - suffix.length();
        return prefix + limit(scenePrompt, sceneBudget) + suffix;
    }

    private String limit(String value, int budget) {
        return value.length() <= budget
                ? value
                : value.substring(0, Math.max(0, budget)).stripTrailing();
    }
}
