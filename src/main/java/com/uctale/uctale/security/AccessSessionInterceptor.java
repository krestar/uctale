package com.uctale.uctale.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class AccessSessionInterceptor implements HandlerInterceptor {

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
        return true;
    }
}
