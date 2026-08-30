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
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

@Service
public class AccessSessionService {

    public static final String COOKIE_NAME = "uctale_access";
    public static final String OWNER_COOKIE_NAME = "uctale_owner";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String ACCESS_VERSION = "v1";
    private static final String OWNER_VERSION = "o1";
    private static final Duration DEFAULT_OWNER_TTL = Duration.ofDays(180);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final byte[] signingSecret;
    private final byte[] accessPassword;
    private final Duration ttl;
    private final Duration ownerTtl;
    private final boolean secureCookie;
    private final Clock clock;

    @Autowired
    public AccessSessionService(
            @Value("${game.access.password}") String accessPassword,
            @Value("${game.access.session-secret}") String sessionSecret,
            @Value("${game.access.session-ttl-seconds:3600}") long ttlSeconds,
            @Value("${game.owner.cookie-ttl-seconds:15552000}") long ownerTtlSeconds,
            @Value("${game.access.cookie-secure:true}") boolean secureCookie
    ) {
        this(
                accessPassword,
                sessionSecret,
                Duration.ofSeconds(ttlSeconds),
                Duration.ofSeconds(ownerTtlSeconds),
                secureCookie,
                Clock.systemUTC()
        );
    }

    public AccessSessionService(String accessPassword, String sessionSecret, long ttlSeconds, boolean secureCookie) {
        this(
                accessPassword,
                sessionSecret,
                Duration.ofSeconds(ttlSeconds),
                DEFAULT_OWNER_TTL,
                secureCookie,
                Clock.systemUTC()
        );
    }

    AccessSessionService(
            String accessPassword,
            String sessionSecret,
            Duration ttl,
            Duration ownerTtl,
            boolean secureCookie,
            Clock clock
    ) {
        if (sessionSecret == null || sessionSecret.length() < 32) {
            throw new IllegalArgumentException("game.access.session-secret은 32자 이상이어야 합니다.");
        }
        if (ownerTtl.isNegative() || ownerTtl.isZero()) {
            throw new IllegalArgumentException("game.owner.cookie-ttl-seconds는 양수여야 합니다.");
        }
        this.accessPassword = accessPassword.getBytes(StandardCharsets.UTF_8);
        this.signingSecret = sessionSecret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
        this.ownerTtl = ownerTtl;
        this.secureCookie = secureCookie;
        this.clock = clock;
    }

    public IssuedSession authenticate(String candidatePassword, String ownerToken) {
        verifyPassword(candidatePassword);
        String ownerKey = ownerKeyFromToken(ownerToken).orElseGet(this::generateOwnerKey);
        return new IssuedSession(issueAccessToken(ownerKey), issueOwnerToken(ownerKey), ownerKey);
    }

    public String authenticate(String candidatePassword) {
        return authenticate(candidatePassword, null).accessToken();
    }

    public AccessPrincipal validateAndGetPrincipal(String token) {
        ParsedAccessToken parsed = parseAccessToken(token);
        if (!Instant.ofEpochSecond(parsed.expiresAt()).isAfter(clock.instant())) {
            throw new AccessSessionException("ACCESS_SESSION_EXPIRED", "접근 세션이 만료되었습니다.");
        }
        return new AccessPrincipal(parsed.ownerKey());
    }

    public void validate(String token) {
        validateAndGetPrincipal(token);
    }

    public Optional<String> ownerKeyFromToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !OWNER_VERSION.equals(parts[0]) || !isValidOwnerKey(parts[1])) {
            return Optional.empty();
        }

        String payload = "owner." + parts[0] + "." + parts[1];
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(sign(payload), actual)) {
            return Optional.empty();
        }
        return Optional.of(parts[1]);
    }

    public String issueOwnerToken(String ownerKey) {
        if (!isValidOwnerKey(ownerKey)) {
            throw new IllegalArgumentException("owner key가 올바르지 않습니다.");
        }
        String value = OWNER_VERSION + "." + ownerKey;
        String payload = "owner." + value;
        return value + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
    }

    public Duration ttl() {
        return ttl;
    }

    public Duration ownerTtl() {
        return ownerTtl;
    }

    public boolean secureCookie() {
        return secureCookie;
    }

    public String sameSite() {
        return secureCookie ? "None" : "Lax";
    }

    private void verifyPassword(String candidatePassword) {
        byte[] candidate = candidatePassword == null
                ? new byte[0]
                : candidatePassword.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(accessPassword, candidate)) {
            throw new AccessSessionException("INVALID_CREDENTIALS", "비밀번호가 올바르지 않습니다.");
        }
    }

    private String generateOwnerKey() {
        byte[] random = new byte[24];
        SECURE_RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private String issueAccessToken(String ownerKey) {
        long expiresAt = clock.instant().plus(ttl).getEpochSecond();
        String payload = ACCESS_VERSION + "." + expiresAt + "." + ownerKey;
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
    }

    private ParsedAccessToken parseAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw new AccessSessionException("ACCESS_SESSION_REQUIRED", "접근 인증이 필요합니다.");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 4 || !ACCESS_VERSION.equals(parts[0]) || !isValidOwnerKey(parts[2])) {
            throw invalid();
        }

        long expiresAt;
        try {
            expiresAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException exception) {
            throw invalid();
        }

        String payload = String.join(".", parts[0], parts[1], parts[2]);
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(parts[3]);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        if (!MessageDigest.isEqual(sign(payload), actual)) {
            throw invalid();
        }
        return new ParsedAccessToken(expiresAt, parts[2]);
    }

    private boolean isValidOwnerKey(String ownerKey) {
        if (ownerKey == null || ownerKey.isBlank() || ownerKey.length() > 64) {
            return false;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(ownerKey);
            return decoded.length >= 18 && decoded.length <= 32
                    && Arrays.equals(
                    Base64.getUrlEncoder().withoutPadding().encode(decoded),
                    ownerKey.getBytes(StandardCharsets.US_ASCII)
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
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

    public record IssuedSession(String accessToken, String ownerToken, String ownerKey) {}

    public record AccessPrincipal(String ownerKey) {}

    private record ParsedAccessToken(long expiresAt, String ownerKey) {}
}
