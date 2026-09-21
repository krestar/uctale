package com.uctale.uctale.application.narrative;

public class NarrativeRecoveryDeferredException extends InvalidNarrativeResponseException {

    private final int retryCount;
    private final String reasonCode;
    private final long retryAfterSeconds;

    public NarrativeRecoveryDeferredException(
            int retryCount,
            String reasonCode,
            long retryAfterSeconds,
            Throwable cause
    ) {
        super("Narrative provider 복구 대기 시간이 필요합니다.", cause);
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount는 0 이상이어야 합니다.");
        }
        if (retryAfterSeconds < 1) {
            throw new IllegalArgumentException("retryAfterSeconds는 1 이상이어야 합니다.");
        }
        this.retryCount = retryCount;
        this.reasonCode = reasonCode == null || reasonCode.isBlank() ? "UNKNOWN" : reasonCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int retryCount() {
        return retryCount;
    }

    public String reasonCode() {
        return reasonCode;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
