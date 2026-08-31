package com.uctale.uctale.application.cost;

public class ProviderBudgetExceededException extends RuntimeException {
    public ProviderBudgetExceededException(String message) {
        super(message);
    }
}
