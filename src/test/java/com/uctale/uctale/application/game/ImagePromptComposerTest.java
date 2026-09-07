package com.uctale.uctale.application.game;

import com.uctale.uctale.application.narrative.NarrativeTurn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImagePromptComposerTest {

    private final ImagePromptComposer composer = new ImagePromptComposer();

    @Test
    @DisplayName("대표 장면과 색채 스트레스 fixture는 같은 입력에서 항상 같은 v3 prompt를 만든다")
    void representativeFixtures_AreDeterministic() {
        List<NarrativeTurn.VisualAssets> fixtures = List.of(
                assets("subway platform", List.of("office worker"), List.of("briefcase")),
                assets("sunlit classroom", List.of("student"), List.of("notebook")),
                assets("rainy alley", List.of("detective"), List.of("umbrella")),
                assets("forest clearing", List.of("giant wolf"), List.of()),
                assets("castle hall", List.of("knight", "mage"), List.of("broken shield")),
                assets("abandoned mall", List.of("zombie horde"), List.of("shopping cart")),
                assets("rooftop at night", List.of("sniper"), List.of("radio")),
                assets("quiet apartment", List.of("elderly npc"), List.of("tea cup")),
                assets("underground arena", List.of("fighter", "armored monster"), List.of("spear")),
                assets("ancient shrine", List.of(), List.of("glowing relic")),
                assets("snowy mountain pass", List.of("traveler"), List.of("map")),
                assets("bright seaside town", List.of("merchant", "child"), List.of("fruit stand")),
                assets("dark laboratory", List.of("scientist"), List.of("sealed capsule")),
                assets("burning battlefield", List.of("soldier", "dragon"), List.of("banner")),
                assets("train interior", List.of("passenger", "conductor"), List.of("ticket")),
                assets("desert ruins", List.of("explorer"), List.of("ancient key")),
                assets("Seoul skyline under a nuclear explosion and huge orange mushroom cloud", List.of(), List.of()),
                assets("burning city street with red flames", List.of("firefighter"), List.of()),
                assets("neon city alley with glowing pink and blue signs", List.of("pedestrian"), List.of()),
                assets("vivid red and orange sunset over the ocean", List.of(), List.of()),
                assets("brightly colored festival with multicolor lanterns", List.of("crowd"), List.of("lanterns"))
        );

        assertThat(fixtures).hasSize(21);
        for (NarrativeTurn.VisualAssets fixture : fixtures) {
            String first = composer.compose(fixture);
            String second = composer.compose(fixture);
            assertThat(first).isEqualTo(second);
            assertThat(first)
                    .startsWith("style[uctale-charcoal-v3]: raw monochrome charcoal/graphite sketch")
                    .contains(
                            "rough uneven linework",
                            "high-contrast black/white structure",
                            "grayscale only",
                            "coarse paper grain",
                            "dense cross-hatching",
                            "smudged deep shadows",
                            "erased/scraped white highlights",
                            "no colored pigment/accent",
                            "final style lock:"
                    );
            assertThat(first.length()).isLessThanOrEqualTo(1_800);
        }
    }

    @Test
    @DisplayName("v3 prompt는 raw medium을 장면보다 먼저 고정하고 color-prone 장면 의미를 보존한다")
    void compose_V3StyleLocksChromaticSceneWithoutDeletingMeaning() {
        String prompt = composer.compose(assets(
                "Seoul skyline under a nuclear explosion with an orange mushroom cloud, red fire, neon signs, and vivid sunset",
                List.of(),
                List.of("glowing emergency sign")
        ));

        assertThat(prompt.indexOf("style[uctale-charcoal-v3]")).isLessThan(prompt.indexOf("setting:"));
        assertThat(prompt)
                .contains(
                        "nuclear explosion",
                        "orange mushroom cloud",
                        "red fire",
                        "neon signs",
                        "vivid sunset",
                        "glowing emergency sign"
                )
                .contains("black/gray/white tonal values only")
                .contains("no colored accent")
                .contains("polished/editorial digital illustration");
    }

    @Test
    @DisplayName("v3 prompt는 중복과 공백을 제거하고 고정 순서의 golden 문자열을 만든다")
    void compose_V3GoldenPrompt() {
        NarrativeTurn.VisualAssets assets = assets(
                "  ruined   station  ",
                List.of("Hunter", " hunter ", "Black wolf"),
                List.of("rusted sword", "RUSTED SWORD")
        );

        assertThat(composer.compose(assets)).isEqualTo(
                "style[uctale-charcoal-v3]: raw monochrome charcoal/graphite sketch on coarse off-white paper; "
                        + "rough uneven linework, high-contrast black/white structure, grayscale only; coarse paper grain, "
                        + "dry-media texture, dense cross-hatching, scratched graphite, smudged deep shadows, "
                        + "erased/scraped white highlights; imperfect raw concept-sketch finish; no colored pigment/accent, "
                        + "polished/editorial digital illustration, watercolor, oil painting, photorealism, or 3D render; "
                        + "subjects: Hunter, Black wolf; objects: rusted sword; setting: ruined station; "
                        + "atmosphere: deep monochrome shadows, charcoal energy; "
                        + "composition: readable silhouettes, strong depth, raw hand-drawn layering; "
                        + "final style lock: raw charcoal/graphite only; preserve fire, explosions, neon, sunsets and glowing objects, "
                        + "but render color-prone subjects in black/gray/white tonal values only; no colored accent"
        );
    }

    @Test
    @DisplayName("v2를 명시하면 기존 golden prompt를 그대로 유지한다")
    void legacyV2_PreservesGoldenPrompt() {
        ImagePromptComposer v2 = new ImagePromptComposer("uctale-charcoal-v2");
        NarrativeTurn.VisualAssets assets = assets(
                "  ruined   station  ",
                List.of("Hunter", " hunter ", "Black wolf"),
                List.of("rusted sword", "RUSTED SWORD")
        );

        assertThat(v2.compose(assets)).isEqualTo(
                "style[uctale-charcoal-v2]: monochrome charcoal and graphite drawing on off-white paper, "
                        + "grayscale only, visible charcoal grain, smudged shading, expressive hand-drawn strokes, "
                        + "no colored pigments or color accents, no watercolor, no oil painting, no digital color painting, "
                        + "no photorealism, no 3D render; subjects: Hunter, Black wolf; objects: rusted sword; "
                        + "setting: ruined station; atmosphere: narrative editorial scene with restrained tonal drama; "
                        + "composition: clear focal point, readable silhouettes, layered hand-drawn depth; "
                        + "final style lock: monochrome charcoal and graphite only; render fire, explosions, neon, sunsets, "
                        + "and glowing objects using black, gray, and white tonal values only; no color"
        );
    }

    @Test
    @DisplayName("v1을 명시하면 기존 golden prompt를 그대로 유지한다")
    void legacyV1_PreservesGoldenPrompt() {
        ImagePromptComposer legacy = new ImagePromptComposer("uctale-charcoal-v1");
        NarrativeTurn.VisualAssets assets = assets(
                "  ruined   station  ",
                List.of("Hunter", " hunter ", "Black wolf"),
                List.of("rusted sword", "RUSTED SWORD")
        );

        assertThat(legacy.compose(assets)).isEqualTo(
                "subjects: Hunter, Black wolf; objects: rusted sword; setting: ruined station; "
                        + "atmosphere: dramatic storybook scene; "
                        + "composition: clear focal point, readable silhouettes, cinematic depth; "
                        + "style[uctale-charcoal-v1]: rough charcoal sketch, high contrast black and white, "
                        + "gritty paper texture, expressive pencil strokes, no colors, story concept art"
        );
    }

    @Test
    @DisplayName("긴 장면도 v3 style lock을 보존하며 최소 1000자 장면 예산과 전체 길이 제한을 유지한다")
    void longScene_PreservesStyleContractWithinLimit() {
        String longSetting = "burning neon sunset battlefield ".repeat(100);

        String prompt = composer.compose(assets(longSetting, List.of("soldier"), List.of("flare")));

        int sceneStart = prompt.indexOf("subjects:");
        int sceneEnd = prompt.indexOf("; atmosphere:");
        assertThat(prompt.length()).isLessThanOrEqualTo(1_800);
        assertThat(sceneEnd - sceneStart).isGreaterThanOrEqualTo(1_000);
        assertThat(prompt)
                .startsWith("style[uctale-charcoal-v3]")
                .contains("setting: burning neon sunset battlefield")
                .endsWith("no colored accent");
    }

    @Test
    @DisplayName("시각 요소가 없으면 null이고 fallback도 기본 v3 style 계약을 사용한다")
    void emptyAssets_AndFallback() {
        assertThat(composer.compose(assets(" ", List.of(), List.of()))).isNull();
        assertThat(composer.composeFallback("zombie apocalypse"))
                .startsWith("style[uctale-charcoal-v3]")
                .contains("setting: zombie apocalypse", "final style lock:");
    }

    @Test
    @DisplayName("지원하지 않는 style version은 조용히 기본값으로 복구하지 않는다")
    void unsupportedStyleVersion_IsRejected() {
        assertThatThrownBy(() -> new ImagePromptComposer("uctale-charcoal-v4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 이미지 style version");
    }

    private NarrativeTurn.VisualAssets assets(String background, List<String> characters, List<String> objects) {
        return new NarrativeTurn.VisualAssets(background, characters, objects);
    }
}
