package com.uctale.uctale.provider.gemini;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public final class GeminiProviderSettings {

    private static final Pattern STABLE_FLASH_MODEL = Pattern.compile("^gemini-(\\d+)\\.(\\d+)-flash$");
    private static final String GENERATE_CONTENT_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final String apiKey;
    private final String modelId;
    private final int modelMajor;
    private final int modelMinor;
    private final ThinkingLevel openingThinkingLevel;
    private final ThinkingLevel progressThinkingLevel;

    public GeminiProviderSettings(
            @Value("${google.ai.api-key}") String apiKey,
            @Value("${google.ai.model}") String modelId,
            @Value("${google.ai.thinking.opening}") String openingThinkingLevel,
            @Value("${google.ai.thinking.progress}") String progressThinkingLevel
    ) {
        this.apiKey = requireNonBlank(apiKey, "Gemini API key");
        this.modelId = requireNonBlank(modelId, "Gemini model ID");

        Matcher matcher = STABLE_FLASH_MODEL.matcher(this.modelId);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Gemini Narrative 모델은 명시적인 stable Flash model ID여야 합니다: " + this.modelId
            );
        }
        this.modelMajor = Integer.parseInt(matcher.group(1));
        this.modelMinor = Integer.parseInt(matcher.group(2));
        if (!supportsConfiguredThinkingContract()) {
            throw new IllegalArgumentException("지원하지 않는 Gemini Narrative model family입니다: " + this.modelId);
        }

        this.openingThinkingLevel = ThinkingLevel.parse(openingThinkingLevel, "opening");
        this.progressThinkingLevel = ThinkingLevel.parse(progressThinkingLevel, "progress");
    }

    String apiKey() {
        return apiKey;
    }

    public String modelId() {
        return modelId;
    }

    String generateContentUrl() {
        return GENERATE_CONTENT_URL.formatted(modelId);
    }

    ThinkingLevel openingThinkingLevel() {
        return openingThinkingLevel;
    }

    ThinkingLevel progressThinkingLevel() {
        return progressThinkingLevel;
    }

    Map<String, Object> thinkingConfig(ThinkingLevel level) {
        if (modelMajor == 2 && modelMinor == 5) {
            return Map.of("thinkingBudget", level.legacyThinkingBudget());
        }
        return Map.of("thinkingLevel", level.apiValue());
    }

    private boolean supportsConfiguredThinkingContract() {
        return (modelMajor == 2 && modelMinor == 5) || modelMajor == 3;
    }

    private String requireNonBlank(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(label + "가 비어 있습니다.");
        }
        return normalized;
    }

    enum ThinkingLevel {
        LOW("low", 1_024),
        MEDIUM("medium", -1),
        HIGH("high", 24_576);

        private final String apiValue;
        private final int legacyThinkingBudget;

        ThinkingLevel(String apiValue, int legacyThinkingBudget) {
            this.apiValue = apiValue;
            this.legacyThinkingBudget = legacyThinkingBudget;
        }

        String apiValue() {
            return apiValue;
        }

        int legacyThinkingBudget() {
            return legacyThinkingBudget;
        }

        static ThinkingLevel parse(String value, String operation) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            for (ThinkingLevel level : values()) {
                if (level.apiValue.equals(normalized)) {
                    return level;
                }
            }
            throw new IllegalArgumentException(
                    "지원하지 않는 Gemini " + operation + " thinking level입니다: " + normalized
                            + " (allowed: low, medium, high)"
            );
        }
    }
}
