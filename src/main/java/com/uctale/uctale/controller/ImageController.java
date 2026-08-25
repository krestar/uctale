package com.uctale.uctale.controller;

import com.uctale.uctale.application.image.ImageGenerator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@Validated
@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
public class ImageController {

    private static final int MAX_PROMPT_LENGTH = 2000;

    private final ImageGenerator imageGenerator;

    @GetMapping("/image")
    public ResponseEntity<byte[]> image(
            @RequestParam
            @NotBlank(message = "이미지 prompt는 필수입니다.")
            @Size(max = MAX_PROMPT_LENGTH, message = "이미지 prompt는 2000자 이하여야 합니다.")
            String prompt,
            @RequestParam(defaultValue = "16:9")
            @Pattern(regexp = "^(16:9|1:1)$", message = "지원하지 않는 이미지 비율입니다.")
            String aspectRatio
    ) {
        validateRequest(prompt, aspectRatio);

        ImageGenerator.GeneratedImage generatedImage = imageGenerator.fetchImage(prompt, aspectRatio);
        if (generatedImage == null || generatedImage.bytes() == null || generatedImage.bytes().length == 0) {
            return ResponseEntity.status(502).build();
        }

        return ResponseEntity.ok()
                .contentType(generatedImage.contentType())
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic())
                .body(generatedImage.bytes());
    }

    private void validateRequest(String prompt, String aspectRatio) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("이미지 prompt는 필수입니다.");
        }
        if (prompt.length() > MAX_PROMPT_LENGTH) {
            throw new IllegalArgumentException("이미지 prompt는 2000자 이하여야 합니다.");
        }
        if (!"16:9".equals(aspectRatio) && !"1:1".equals(aspectRatio)) {
            throw new IllegalArgumentException("지원하지 않는 이미지 비율입니다.");
        }
    }
}
