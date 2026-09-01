package com.uctale.uctale.provider.gemini;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiProviderSettingsTest {

    @Test
    @DisplayName("Gemini 3 stable Flash는 model URL과 thinkingLevel 계약을 설정에서 만든다")
    void gemini3Stable_UsesThinkingLevel() {
        GeminiProviderSettings settings = new GeminiProviderSettings(
                "key", "gemini-3.7-flash", "medium", "low"
        );

        assertThat(settings.modelId()).isEqualTo("gemini-3.7-flash");
        assertThat(settings.generateContentUrl()).endsWith("/models/gemini-3.7-flash:generateContent");
        assertThat(settings.thinkingConfig(settings.openingThinkingLevel()))
                .containsEntry("thinkingLevel", "medium")
                .doesNotContainKey("thinkingBudget");
        assertThat(settings.thinkingConfig(settings.progressThinkingLevel()))
                .containsEntry("thinkingLevel", "low");
    }

    @Test
    @DisplayName("Gemini 2.5 rollback은 legacy thinkingBudget 경계에서 호환한다")
    void gemini25Stable_UsesLegacyThinkingBudget() {
        GeminiProviderSettings settings = new GeminiProviderSettings(
                "key", "gemini-2.5-flash", "medium", "low"
        );

        assertThat(settings.thinkingConfig(settings.openingThinkingLevel()))
                .containsEntry("thinkingBudget", -1)
                .doesNotContainKey("thinkingLevel");
        assertThat(settings.thinkingConfig(settings.progressThinkingLevel()))
                .containsEntry("thinkingBudget", 1_024);
    }

    @Test
    @DisplayName("검증하지 않은 future major Flash는 자동 호환한다고 가정하지 않는다")
    void unreviewedFutureMajor_IsRejected() {
        assertThatThrownBy(() -> new GeminiProviderSettings("key", "gemini-4.0-flash", "high", "medium"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 Gemini Narrative model family");
    }

    @Test
    @DisplayName("latest preview experimental alias는 production model 설정으로 거부한다")
    void unstableAliases_AreRejected() {
        assertThatThrownBy(() -> new GeminiProviderSettings("key", "gemini-flash-latest", "medium", "low"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stable Flash model ID");
        assertThatThrownBy(() -> new GeminiProviderSettings("key", "gemini-3.7-flash-preview", "medium", "low"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stable Flash model ID");
    }

    @Test
    @DisplayName("지원하지 않는 thinking level은 시작 시 fail-fast 한다")
    void unsupportedThinkingLevel_IsRejected() {
        assertThatThrownBy(() -> new GeminiProviderSettings("key", "gemini-3.7-flash", "minimal", "low"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowed: low, medium, high");
    }
}
