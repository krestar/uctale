package com.uctale.uctale.security;

public class AccessRequestForbiddenException extends RuntimeException {
    public AccessRequestForbiddenException(String message) {
        super(message);
    }
}
