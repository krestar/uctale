package com.uctale.uctale.application.image;

public interface ImageProviderFailure {
    int status();
    String code();
    String requestId();
    Long retryAfterSeconds();
    int retryCount();
}
