package com.uctale.uctale.application.narrative;

public class NarrativeRecoveryInterruptedException extends RuntimeException {

    private final int retryCount;
    private final RuntimeException originalCause;

    public NarrativeRecoveryInterruptedException(int retryCount, RuntimeException originalCause) {
        super("Narrative recovery가 완료되기 전에 중단되었습니다.", originalCause);
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount는 0 이상이어야 합니다.");
        }
        if (originalCause == null) {
            throw new IllegalArgumentException("원래 예외는 필수입니다.");
        }
        this.retryCount = retryCount;
        this.originalCause = originalCause;
    }

    public int retryCount() {
        return retryCount;
    }

    public RuntimeException originalCause() {
        return originalCause;
    }
}
