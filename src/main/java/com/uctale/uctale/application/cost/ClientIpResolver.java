package com.uctale.uctale.application.cost;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final int MAX_IP_TEXT_LENGTH = 128;

    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String firstHop = forwardedFor.split(",", 2)[0].trim();
            if (!firstHop.isBlank()) {
                return truncate(firstHop);
            }
        }
        return truncate(request.getRemoteAddr());
    }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim();
        return normalized.length() <= MAX_IP_TEXT_LENGTH
                ? normalized
                : normalized.substring(0, MAX_IP_TEXT_LENGTH);
    }
}
