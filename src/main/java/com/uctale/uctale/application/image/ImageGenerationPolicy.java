package com.uctale.uctale.application.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ImageGenerationPolicy {

    private static final int MIN_DIMENSION = 64;
    private static final int MAX_DIMENSION = 4096;

    private final String model;
    private final int width;
    private final int height;
    private final int squareSize;
    private final boolean safe;
    private final String styleVersion;
    private final SecureRandom secureRandom;

    public ImageGenerationPolicy(
            @Value("${game.image.model:flux}") String model,
            @Value("${game.image.width:1024}") int width,
            @Value("${game.image.height:576}") int height,
            @Value("${game.image.square-size:768}") int squareSize,
            @Value("${game.image.safe:true}") boolean safe,
            @Value("${game.image.style-version:uctale-charcoal-v1}") String styleVersion
    ) {
        this(model, width, height, squareSize, safe, styleVersion, new SecureRandom());
    }

    ImageGenerationPolicy(
            String model,
            int width,
            int height,
            int squareSize,
            boolean safe,
            String styleVersion,
            SecureRandom secureRandom
    ) {
        this.model = requireText(model, "이미지 model");
        this.width = validateDimension(width, "이미지 width");
        this.height = validateDimension(height, "이미지 height");
        this.squareSize = validateDimension(squareSize, "이미지 square size");
        this.safe = safe;
        this.styleVersion = requireText(styleVersion, "이미지 style version");
        this.secureRandom = secureRandom;
    }

    public GenerationSpec issue(String aspectRatio) {
        boolean square = "1:1".equals(aspectRatio);
        return new GenerationSpec(
                model,
                square ? squareSize : width,
                square ? squareSize : height,
                secureRandom.nextInt(Integer.MAX_VALUE),
                safe,
                styleVersion
        );
    }

    private int validateDimension(int value, String name) {
        if (value < MIN_DIMENSION || value > MAX_DIMENSION) {
            throw new IllegalArgumentException(name + "는 64~4096 범위여야 합니다.");
        }
        return value;
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "은(는) 필수입니다.");
        }
        return value.trim();
    }

    public record GenerationSpec(
            String model,
            int width,
            int height,
            int seed,
            boolean safe,
            String styleVersion
    ) {}
}
