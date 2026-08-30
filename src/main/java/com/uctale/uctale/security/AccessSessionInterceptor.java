package com.uctale.uctale.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class AccessSessionInterceptor implements HandlerInterceptor {

    public static final String CLIENT_HEADER = "X-UCTale-Client";
    public static final String CLIENT_HEADER_VALUE = "web";
    public static final String OWNER_KEY_ATTRIBUTE = "uctale.ownerKey";

    private final AccessSessionService accessSessionService;

    public AccessSessionInterceptor(AccessSessionService accessSessionService) {
        this.accessSessionService = accessSessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String accessToken = findCookie(request, AccessSessionService.COOKIE_NAME);
        AccessSessionService.AccessPrincipal principal = accessSessionService.validateAndGetPrincipal(accessToken);

        if (!CLIENT_HEADER_VALUE.equals(request.getHeader(CLIENT_HEADER))) {
            throw new AccessRequestForbiddenException("허용된 클라이언트 요청이 아닙니다.");
        }

        request.setAttribute(OWNER_KEY_ATTRIBUTE, principal.ownerKey());
        ensureOwnerCookie(request, response, principal.ownerKey());
        return true;
    }

    private void ensureOwnerCookie(HttpServletRequest request, HttpServletResponse response, String ownerKey) {
        String ownerToken = findCookie(request, AccessSessionService.OWNER_COOKIE_NAME);
        boolean ownerMatches = accessSessionService.ownerKeyFromToken(ownerToken)
                .filter(ownerKey::equals)
                .isPresent();
        if (ownerMatches) {
            return;
        }

        ResponseCookie ownerCookie = ResponseCookie.from(
                        AccessSessionService.OWNER_COOKIE_NAME,
                        accessSessionService.issueOwnerToken(ownerKey)
                )
                .httpOnly(true)
                .secure(accessSessionService.secureCookie())
                .sameSite(accessSessionService.sameSite())
                .path("/api/game")
                .maxAge(accessSessionService.ownerTtl())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, ownerCookie.toString());
    }

    private String findCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
