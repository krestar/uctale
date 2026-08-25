package com.uctale.uctale.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class AccessSessionInterceptor implements HandlerInterceptor {

    public static final String CLIENT_HEADER = "X-UCTale-Client";
    public static final String CLIENT_HEADER_VALUE = "web";

    private final AccessSessionService accessSessionService;

    public AccessSessionInterceptor(AccessSessionService accessSessionService) {
        this.accessSessionService = accessSessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getCookies() == null
                ? null
                : Arrays.stream(request.getCookies())
                .filter(cookie -> AccessSessionService.COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);

        accessSessionService.validate(token);

        if (!CLIENT_HEADER_VALUE.equals(request.getHeader(CLIENT_HEADER))) {
            throw new AccessRequestForbiddenException("허용된 클라이언트 요청이 아닙니다.");
        }
        return true;
    }
}
