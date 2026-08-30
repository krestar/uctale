package com.uctale.uctale.application.game;

import com.uctale.uctale.application.narrative.NarrativeTurn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImagePromptComposerTest {

    private final ImagePromptComposer composer = new ImagePromptComposer();

    @Test
    @DisplayName("대표 장면 fixture 16개는 같은 입력에서 항상 같은 prompt를 만든다")
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
                assets("desert ruins", List.of("explorer"), List.of("ancient key"))
        );

        assertThat(fixtures).hasSize(16);
        for (NarrativeTurn.VisualAssets fixture : fixtures) {
            String first = composer.compose(fixture);
            String second = composer.compose(fixture);
            assertThat(first).isEqualTo(second);
            assertThat(first).contains("atmosphere:", "composition:", "style[uctale-charcoal-v1]");
            assertThat(first.length()).isLessThanOrEqualTo(1_800);
        }
    }

    @Test
    @DisplayName("prompt는 중복과 공백을 제거하고 고정 순서의 golden 문자열을 만든다")
    void compose_GoldenPrompt() {
        NarrativeTurn.VisualAssets assets = assets(
                "  ruined   station  ",
                List.of("Hunter", " hunter ", "Black wolf"),
                List.of("rusted sword", "RUSTED SWORD")
        );

        assertThat(composer.compose(assets)).isEqualTo(
                "subjects: Hunter, Black wolf; objects: rusted sword; setting: ruined station; "
                        + "atmosphere: dramatic storybook scene; "
                        + "composition: clear focal point, readable silhouettes, cinematic depth; "
                        + "style[uctale-charcoal-v1]: rough charcoal sketch, high contrast black and white, "
                        + "gritty paper texture, expressive pencil strokes, no colors, story concept art"
        );
    }

    @Test
    @DisplayName("시각 요소가 없으면 null이고 fallback도 동일 style 계약을 사용한다")
    void emptyAssets_AndFallback() {
        assertThat(composer.compose(assets(" ", List.of(), List.of()))).isNull();
        assertThat(composer.composeFallback("zombie apocalypse"))
                .startsWith("setting: zombie apocalypse;")
                .contains("style[uctale-charcoal-v1]");
    }

    private NarrativeTurn.VisualAssets assets(String background, List<String> characters, List<String> objects) {
        return new NarrativeTurn.VisualAssets(background, characters, objects);
    }
}
