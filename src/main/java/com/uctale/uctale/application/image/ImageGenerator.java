package com.uctale.uctale.application.image;

import org.springframework.http.MediaType;

public interface ImageGenerator {

    GeneratedImage fetchImage(String prompt, String aspectRatio);

    record GeneratedImage(byte[] bytes, MediaType contentType) {}
}
