package com.uctale.uctale.application.narrative;

public class RecoverableNarrativeResponseException extends InvalidNarrativeResponseException {

    private final String reasonCode;

    public RecoverableNarrativeResponseException(String reasonCode, String message) {
        super(message);
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("Narrative recovery reason code는 필수입니다.");
        }
        this.reasonCode = reasonCode;
    }

    public RecoverableNarrativeResponseException(String reasonCode, String message, Throwable cause) {
        super(message, cause);
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("Narrative recovery reason code는 필수입니다.");
        }
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
