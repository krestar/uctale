package com.uctale.uctale.provider.image;

import com.uctale.uctale.application.image.ImageGenerationException;
import com.uctale.uctale.application.image.ImageProviderFailure;

public class PollinationsProviderException extends ImageGenerationException implements ImageProviderFailure {

    private final int status;
    private final String code;
    private final String requestId;
    private final Long retryAfterSeconds;
    private final boolean retryable;
    private final int retryCount;

    public PollinationsProviderException(
            String message,
            int status,
            String code,
            String requestId,
            Long retryAfterSeconds,
            boolean retryable,
            int retryCount
    ) {
        super(message);
        this.status = status;
        this.code = code;
        this.requestId = requestId;
        this.retryAfterSeconds = retryAfterSeconds;
        this.retryable = retryable;
        this.retryCount = retryCount;
    }

    public PollinationsProviderException(String message, Throwable cause, boolean retryable, int retryCount) {
        super(message, cause);
        this.status = 0;
        this.code = "NETWORK_ERROR";
        this.requestId = null;
        this.retryAfterSeconds = null;
        this.retryable = retryable;
        this.retryCount = retryCount;
    }

    PollinationsProviderException withRetryCount(int value) {
        return new PollinationsProviderException(
                getMessage(), status, code, requestId, retryAfterSeconds, retryable, value
        );
    }

    @Override
    public int status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String requestId() {
        return requestId;
    }

    @Override
    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public boolean retryable() {
        return retryable;
    }

    @Override
    public int retryCount() {
        return retryCount;
    }
}
