package com.uctale.uctale.application.game;

import com.uctale.uctale.application.narrative.NarrativeTurn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImagePromptComposerTest {

    private final ImagePromptComposer composer = new ImagePromptComposer();

    @Test
    @DisplayName("대표 장면과 색채 스트레스 fixture는 같은 입력에서 항상 같은 v2 prompt를 만든다")
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
                    .startsWith("style[uctale-charcoal-v2]: monochrome charcoal and graphite drawing")
                    .contains("grayscale only", "atmosphere:", "composition:", "final style lock:", "no color");
            assertThat(first.length()).isLessThanOrEqualTo(1_800);
        }
    }

    @Test
    @DisplayName("v2 prompt는 style을 장면보다 먼저 고정하고 장면 의미는 제거하지 않는다")
    void compose_V2StyleLocksChromaticScene() {
        String prompt = composer.compose(assets(
                "Seoul skyline under a nuclear explosion with an orange mushroom cloud and red fire",
                List.of(),
                List.of("glowing emergency sign")
        ));

        assertThat(prompt.indexOf("style[uctale-charcoal-v2]")).isLessThan(prompt.indexOf("setting:"));
        assertThat(prompt)
                .contains("nuclear explosion", "orange mushroom cloud", "red fire", "glowing emergency sign")
                .contains("black, gray, and white tonal values only")
                .endsWith("no color");
    }

    @Test
    @DisplayName("v2 prompt는 중복과 공백을 제거하고 고정 순서의 golden 문자열을 만든다")
    void compose_GoldenPrompt() {
        NarrativeTurn.VisualAssets assets = assets(
                "  ruined   station  ",
                List.of("Hunter", " hunter ", "Black wolf"),
                List.of("rusted sword", "RUSTED SWORD")
        );

        assertThat(composer.compose(assets)).isEqualTo(
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
    @DisplayName("시각 요소가 없으면 null이고 fallback도 기본 v2 style 계약을 사용한다")
    void emptyAssets_AndFallback() {
        assertThat(composer.compose(assets(" ", List.of(), List.of()))).isNull();
        assertThat(composer.composeFallback("zombie apocalypse"))
                .startsWith("style[uctale-charcoal-v2]")
                .contains("setting: zombie apocalypse", "final style lock:");
    }

    private NarrativeTurn.VisualAssets assets(String background, List<String> characters, List<String> objects) {
        return new NarrativeTurn.VisualAssets(background, characters, objects);
    }
}
