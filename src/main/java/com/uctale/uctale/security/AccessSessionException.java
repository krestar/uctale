package com.uctale.uctale.security;

public class AccessSessionException extends RuntimeException {
    private final String code;

    public AccessSessionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
