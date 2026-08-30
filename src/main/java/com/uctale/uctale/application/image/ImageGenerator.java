package com.uctale.uctale.application.image;

import org.springframework.http.MediaType;

public interface ImageGenerator {

    GeneratedImage fetchImage(GenerationRequest request);

    record GenerationRequest(
            String prompt,
            String model,
            int width,
            int height,
            int seed,
            boolean safe,
            String styleVersion
    ) {
        public GenerationRequest {
            if (prompt == null || prompt.isBlank()) {
                throw new IllegalArgumentException("이미지 prompt는 필수입니다.");
            }
            if (model == null || model.isBlank()) {
                throw new IllegalArgumentException("이미지 model은 필수입니다.");
            }
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("이미지 크기는 양수여야 합니다.");
            }
            if (seed < 0) {
                throw new IllegalArgumentException("이미지 seed는 0 이상이어야 합니다.");
            }
            if (styleVersion == null || styleVersion.isBlank()) {
                throw new IllegalArgumentException("이미지 style version은 필수입니다.");
            }
        }
    }

    record GeneratedImage(byte[] bytes, MediaType contentType) {}
}
