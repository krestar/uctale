package com.uctale.uctale.application.cost;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    private static final String CF_CONNECTING_IP = "CF-Connecting-IP";
    private static final int MAX_IP_TEXT_LENGTH = 128;

    private final boolean trustRenderProxyHeader;

    public ClientIpResolver(
            @Value("${game.client-ip.trust-render-proxy-header:false}") boolean trustRenderProxyHeader
    ) {
        this.trustRenderProxyHeader = trustRenderProxyHeader;
    }

    public String resolve(HttpServletRequest request) {
        if (trustRenderProxyHeader) {
            String renderClientIp = request.getHeader(CF_CONNECTING_IP);
            if (renderClientIp != null && !renderClientIp.isBlank()) {
                return truncate(renderClientIp);
            }
        }
        return truncate(request.getRemoteAddr());
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim();
        return normalized.length() <= MAX_IP_TEXT_LENGTH
                ? normalized
                : normalized.substring(0, MAX_IP_TEXT_LENGTH);
    }
}
