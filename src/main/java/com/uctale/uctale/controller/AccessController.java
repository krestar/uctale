package com.uctale.uctale.controller;

import com.uctale.uctale.application.cost.ClientIpResolver;
import com.uctale.uctale.dto.AccessPasswordRequest;
import com.uctale.uctale.security.AccessAuthenticationRateLimiter;
import com.uctale.uctale.security.AccessSessionService;
import com.uctale.uctale.security.OwnerIdentityIssuanceRateLimiter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
@RequestMapping("/api/game")
public class AccessController {

    private final AccessSessionService accessSessionService;
    private final AccessAuthenticationRateLimiter authenticationRateLimiter;
    private final OwnerIdentityIssuanceRateLimiter ownerIdentityIssuanceRateLimiter;
    private final ClientIpResolver clientIpResolver;

    public AccessController(
            AccessSessionService accessSessionService,
            AccessAuthenticationRateLimiter authenticationRateLimiter,
            OwnerIdentityIssuanceRateLimiter ownerIdentityIssuanceRateLimiter,
            ClientIpResolver clientIpResolver
    ) {
        this.accessSessionService = accessSessionService;
        this.authenticationRateLimiter = authenticationRateLimiter;
        this.ownerIdentityIssuanceRateLimiter = ownerIdentityIssuanceRateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/verify-password")
    public ResponseEntity<Void> verifyPassword(
            @Valid @RequestBody AccessPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        String clientIp = clientIpResolver.resolve(servletRequest);
        String existingOwnerToken = findCookie(servletRequest, AccessSessionService.OWNER_COOKIE_NAME);
        AccessSessionService.IssuedSession session = authenticationRateLimiter.authenticate(
                clientIp,
                () -> accessSessionService.authenticate(request.password(), existingOwnerToken)
        );
        if (session.newOwnerIdentity()) {
            ownerIdentityIssuanceRateLimiter.check(clientIp);
        }

        ResponseCookie accessCookie = ResponseCookie.from(AccessSessionService.COOKIE_NAME, session.accessToken())
                .httpOnly(true)
                .secure(accessSessionService.secureCookie())
                .sameSite(accessSessionService.sameSite())
                .path("/api/game")
                .maxAge(accessSessionService.ttl())
                .build();
        ResponseCookie ownerCookie = ResponseCookie.from(AccessSessionService.OWNER_COOKIE_NAME, session.ownerToken())
                .httpOnly(true)
                .secure(accessSessionService.secureCookie())
                .sameSite(accessSessionService.sameSite())
                .path("/api/game")
                .maxAge(accessSessionService.ownerTtl())
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString(), ownerCookie.toString())
                .build();
    }

    @GetMapping("/access-session")
    public ResponseEntity<Void> accessSession() {
        return ResponseEntity.noContent().build();
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
