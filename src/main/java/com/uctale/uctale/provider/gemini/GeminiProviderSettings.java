package com.uctale.uctale.provider.gemini;

import org.springframework.beans.factory.annotation.Autowired;
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
    private final String fallbackModelId;
    private final ModelVersion modelVersion;
    private final ModelVersion fallbackModelVersion;
    private final ThinkingLevel openingThinkingLevel;
    private final ThinkingLevel progressThinkingLevel;

    @Autowired
    public GeminiProviderSettings(
            @Value("${google.ai.api-key}") String apiKey,
            @Value("${google.ai.model}") String modelId,
            @Value("${google.ai.fallback-model:gemini-3.6-flash}") String fallbackModelId,
            @Value("${google.ai.thinking.opening}") String openingThinkingLevel,
            @Value("${google.ai.thinking.progress}") String progressThinkingLevel
    ) {
        this.apiKey = requireNonBlank(apiKey, "Gemini API key");
        this.modelId = requireNonBlank(modelId, "Gemini model ID");
        this.fallbackModelId = requireNonBlank(fallbackModelId, "Gemini fallback model ID");
        this.modelVersion = parseSupportedModel(this.modelId, "Gemini Narrative 모델");
        this.fallbackModelVersion = parseSupportedModel(this.fallbackModelId, "Gemini Narrative fallback 모델");
        this.openingThinkingLevel = ThinkingLevel.parse(openingThinkingLevel, "opening");
        this.progressThinkingLevel = ThinkingLevel.parse(progressThinkingLevel, "progress");
    }

    public GeminiProviderSettings(
            String apiKey,
            String modelId,
            String openingThinkingLevel,
            String progressThinkingLevel
    ) {
        this(apiKey, modelId, "gemini-3.6-flash", openingThinkingLevel, progressThinkingLevel);
    }

    String apiKey() {
        return apiKey;
    }

    public String modelId() {
        return modelId;
    }

    String fallbackModelId() {
        return fallbackModelId;
    }

    String generateContentUrl() {
        return generateContentUrl(modelId);
    }

    String generateContentUrl(String requestedModelId) {
        String normalized = requireNonBlank(requestedModelId, "Gemini model ID");
        if (!normalized.equals(modelId) && !normalized.equals(fallbackModelId)) {
            throw new IllegalArgumentException("설정되지 않은 Gemini model ID입니다: " + normalized);
        }
        return GENERATE_CONTENT_URL.formatted(normalized);
    }

    String retryModelId(String reasonCode) {
        return isProviderTransientReason(reasonCode) ? fallbackModelId : modelId;
    }

    ThinkingLevel openingThinkingLevel() {
        return openingThinkingLevel;
    }

    ThinkingLevel progressThinkingLevel() {
        return progressThinkingLevel;
    }

    Map<String, Object> thinkingConfig(ThinkingLevel level) {
        return thinkingConfig(level, modelId);
    }

    Map<String, Object> thinkingConfig(ThinkingLevel level, String requestedModelId) {
        ModelVersion version;
        if (modelId.equals(requestedModelId)) {
            version = modelVersion;
        } else if (fallbackModelId.equals(requestedModelId)) {
            version = fallbackModelVersion;
        } else {
            throw new IllegalArgumentException("설정되지 않은 Gemini model ID입니다: " + requestedModelId);
        }
        if (version.major() == 2 && version.minor() == 5) {
            return Map.of("thinkingBudget", level.legacyThinkingBudget());
        }
        return Map.of("thinkingLevel", level.apiValue());
    }

    private ModelVersion parseSupportedModel(String value, String label) {
        Matcher matcher = STABLE_FLASH_MODEL.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(label + "은 명시적인 stable Flash model ID여야 합니다: " + value);
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        if (!((major == 2 && minor == 5) || major == 3)) {
            throw new IllegalArgumentException("지원하지 않는 Gemini Narrative model family입니다: " + value);
        }
        return new ModelVersion(major, minor);
    }

    private boolean isProviderTransientReason(String reasonCode) {
        return reasonCode != null && reasonCode.startsWith("PROVIDER_");
    }

    private String requireNonBlank(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(label + "가 비어 있습니다.");
        }
        return normalized;
    }

    private record ModelVersion(int major, int minor) {}

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
