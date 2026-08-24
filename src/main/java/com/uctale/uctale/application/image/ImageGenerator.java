package com.uctale.uctale.application.image;

import org.springframework.http.MediaType;

public interface ImageGenerator {

    String createPublicUrl(String prompt, String aspectRatio);

    GeneratedImage fetchImage(String prompt, String aspectRatio);

    record GeneratedImage(byte[] bytes, MediaType contentType) {}
}
