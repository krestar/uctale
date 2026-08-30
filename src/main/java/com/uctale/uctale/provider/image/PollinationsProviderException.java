package com.uctale.uctale.provider.image;

import com.uctale.uctale.application.image.ImageGenerationException;

public class PollinationsProviderException extends ImageGenerationException {

    private final int status;
    private final String code;
    private final String requestId;
    private final Long retryAfterSeconds;
    private final boolean retryable;

    public PollinationsProviderException(
            String message,
            int status,
            String code,
            String requestId,
            Long retryAfterSeconds,
            boolean retryable
    ) {
        super(message);
        this.status = status;
        this.code = code;
        this.requestId = requestId;
        this.retryAfterSeconds = retryAfterSeconds;
        this.retryable = retryable;
    }

    public PollinationsProviderException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.status = 0;
        this.code = "NETWORK_ERROR";
        this.requestId = null;
        this.retryAfterSeconds = null;
        this.retryable = retryable;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String requestId() {
        return requestId;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public boolean retryable() {
        return retryable;
    }
}
