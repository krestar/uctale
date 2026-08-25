package com.uctale.uctale.controller;

import com.uctale.uctale.dto.AccessPasswordRequest;
import com.uctale.uctale.security.AccessSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/game")
public class AccessController {

    private final AccessSessionService accessSessionService;

    public AccessController(AccessSessionService accessSessionService) {
        this.accessSessionService = accessSessionService;
    }

    @PostMapping("/verify-password")
    public ResponseEntity<Void> verifyPassword(@Valid @RequestBody AccessPasswordRequest request) {
        String token = accessSessionService.authenticate(request.password());
        ResponseCookie cookie = ResponseCookie.from(AccessSessionService.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(accessSessionService.secureCookie())
                .sameSite(accessSessionService.sameSite())
                .path("/api/game")
                .maxAge(accessSessionService.ttl())
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @GetMapping("/access-session")
    public ResponseEntity<Void> accessSession() {
        return ResponseEntity.noContent().build();
    }
}
