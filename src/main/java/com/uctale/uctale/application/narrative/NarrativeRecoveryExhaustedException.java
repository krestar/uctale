package com.uctale.uctale.application.narrative;

public class NarrativeRecoveryExhaustedException extends InvalidNarrativeResponseException {

    private final int retryCount;
    private final String reasonCode;

    public NarrativeRecoveryExhaustedException(int retryCount, String reasonCode, Throwable cause) {
        super("Narrative provider 응답 복구 한도를 초과했습니다.", cause);
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount는 0 이상이어야 합니다.");
        }
        this.retryCount = retryCount;
        this.reasonCode = reasonCode == null || reasonCode.isBlank() ? "UNKNOWN" : reasonCode;
    }

    public int retryCount() {
        return retryCount;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
