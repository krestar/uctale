package com.uctale.uctale.controller;

import com.uctale.uctale.service.NanoBananaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ImageController {

    private final NanoBananaService nanoBananaService;

    @GetMapping("/image")
    public ResponseEntity<byte[]> image(
            @RequestParam String prompt,
            @RequestParam(defaultValue = "16:9") String aspectRatio
    ) {
        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        NanoBananaService.GeneratedImage generatedImage = nanoBananaService.fetchImage(prompt, aspectRatio);
        if (generatedImage == null || generatedImage.bytes() == null || generatedImage.bytes().length == 0) {
            return ResponseEntity.status(502).build();
        }

        return ResponseEntity.ok()
                .contentType(generatedImage.contentType())
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic())
                .body(generatedImage.bytes());
    }
}