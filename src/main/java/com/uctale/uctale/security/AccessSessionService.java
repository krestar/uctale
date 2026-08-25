package com.uctale.uctale.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class AccessSessionService {

    public static final String COOKIE_NAME = "uctale_access";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final byte[] signingSecret;
    private final byte[] accessPassword;
    private final Duration ttl;
    private final boolean secureCookie;
    private final Clock clock;

    @Autowired
    public AccessSessionService(
            @Value("${game.access.password}") String accessPassword,
            @Value("${game.access.session-secret}") String sessionSecret,
            @Value("${game.access.session-ttl-seconds:3600}") long ttlSeconds,
            @Value("${game.access.cookie-secure:true}") boolean secureCookie
    ) {
        this(accessPassword, sessionSecret, Duration.ofSeconds(ttlSeconds), secureCookie, Clock.systemUTC());
    }

    AccessSessionService(String accessPassword, String sessionSecret, Duration ttl, boolean secureCookie, Clock clock) {
        if (sessionSecret == null || sessionSecret.length() < 32) {
            throw new IllegalArgumentException("game.access.session-secret은 32자 이상이어야 합니다.");
        }
        this.accessPassword = accessPassword.getBytes(StandardCharsets.UTF_8);
        this.signingSecret = sessionSecret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
        this.secureCookie = secureCookie;
        this.clock = clock;
    }

    public String authenticate(String candidatePassword) {
        byte[] candidate = candidatePassword == null
                ? new byte[0]
                : candidatePassword.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(accessPassword, candidate)) {
            throw new AccessSessionException("INVALID_CREDENTIALS", "비밀번호가 올바르지 않습니다.");
        }
        return issueToken();
    }

    public void validate(String token) {
        if (token == null || token.isBlank()) {
            throw new AccessSessionException("ACCESS_SESSION_REQUIRED", "접근 인증이 필요합니다.");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 4 || !"v1".equals(parts[0])) {
            throw invalid();
        }

        long expiresAt;
        try {
            expiresAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException exception) {
            throw invalid();
        }

        String payload = String.join(".", parts[0], parts[1], parts[2]);
        byte[] expected = sign(payload);
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(parts[3]);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw invalid();
        }
        if (!Instant.ofEpochSecond(expiresAt).isAfter(clock.instant())) {
            throw new AccessSessionException("ACCESS_SESSION_EXPIRED", "접근 세션이 만료되었습니다.");
        }
    }

    public Duration ttl() {
        return ttl;
    }

    public boolean secureCookie() {
        return secureCookie;
    }

    public String sameSite() {
        return secureCookie ? "None" : "Lax";
    }

    private String issueToken() {
        long expiresAt = clock.instant().plus(ttl).getEpochSecond();
        byte[] nonce = new byte[18];
        SECURE_RANDOM.nextBytes(nonce);
        String payload = "v1." + expiresAt + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingSecret, HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("접근 세션 서명에 실패했습니다.", exception);
        }
    }

    private AccessSessionException invalid() {
        return new AccessSessionException("ACCESS_SESSION_INVALID", "접근 세션이 올바르지 않습니다.");
    }
}
