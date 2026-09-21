package com.uctale.uctale.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccessSessionOwnerTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

    @Test
    @DisplayName("재로그인은 유효한 o2 owner token의 owner key를 재사용한다")
    void authenticate_ReusesOwnerKeyFromCurrentOwnerCookie() {
        AccessSessionService service = serviceAt(NOW, Duration.ofDays(180), 0);

        AccessSessionService.IssuedSession first = service.authenticate("TEST_PASSWORD", null);
        AccessSessionService.IssuedSession renewed = service.authenticate("TEST_PASSWORD", first.ownerToken());

        assertThat(first.ownerToken()).startsWith("o2.");
        assertThat(renewed.ownerKey()).isEqualTo(first.ownerKey());
        assertThat(service.validateAndGetPrincipal(renewed.accessToken()).ownerKey()).isEqualTo(first.ownerKey());
    }

    @Test
    @DisplayName("o2 owner token은 만료 시각 직전까지 유효하고 경계 시각부터 거부된다")
    void ownerToken_ExpiresAtServerBoundary() {
        Duration ownerTtl = Duration.ofSeconds(60);
        AccessSessionService issuedAt = serviceAt(NOW, ownerTtl, 0);
        AccessSessionService.IssuedSession session = issuedAt.authenticate("TEST_PASSWORD", null);

        AccessSessionService beforeExpiry = serviceAt(NOW.plusSeconds(59), ownerTtl, 0);
        AccessSessionService atExpiry = serviceAt(NOW.plusSeconds(60), ownerTtl, 0);

        assertThat(beforeExpiry.ownerKeyFromToken(session.ownerToken())).contains(session.ownerKey());
        assertThat(atExpiry.ownerKeyFromToken(session.ownerToken())).isEmpty();
    }

    @Test
    @DisplayName("만료된 owner token으로 재로그인하면 기존 owner identity를 재사용하지 않는다")
    void authenticate_DoesNotReuseExpiredOwnerToken() {
        Duration ownerTtl = Duration.ofSeconds(60);
        AccessSessionService issuedAt = serviceAt(NOW, ownerTtl, 0);
        AccessSessionService.IssuedSession first = issuedAt.authenticate("TEST_PASSWORD", null);

        AccessSessionService expired = serviceAt(NOW.plusSeconds(60), ownerTtl, 0);
        AccessSessionService.IssuedSession renewed = expired.authenticate("TEST_PASSWORD", first.ownerToken());

        assertThat(renewed.ownerKey()).isNotEqualTo(first.ownerKey());
    }

    @Test
    @DisplayName("o2 owner token의 만료 시각, owner key, 서명을 변조하면 거부된다")
    void ownerToken_RejectsTampering() {
        AccessSessionService service = serviceAt(NOW, Duration.ofDays(180), 0);
        AccessSessionService.IssuedSession session = service.authenticate("TEST_PASSWORD", null);
        String[] parts = session.ownerToken().split("\\.");

        String expiresTampered = String.join(".", parts[0], Long.toString(Long.parseLong(parts[1]) + 1), parts[2], parts[3]);
        String ownerKeyTampered = String.join(".", parts[0], parts[1], "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", parts[3]);
        String signatureTampered = String.join(".", parts[0], parts[1], parts[2], "A" + parts[3].substring(1));

        assertThat(service.ownerKeyFromToken(expiresTampered)).isEmpty();
        assertThat(service.ownerKeyFromToken(ownerKeyTampered)).isEmpty();
        assertThat(service.ownerKeyFromToken(signatureTampered)).isEmpty();
    }

    @Test
    @DisplayName("malformed owner token은 예외 없이 거부된다")
    void ownerToken_RejectsMalformedToken() {
        AccessSessionService service = serviceAt(NOW, Duration.ofDays(180), 0);

        assertThat(service.ownerKeyFromToken("o2")).isEmpty();
        assertThat(service.ownerKeyFromToken("o2.not-a-number.key.signature")).isEmpty();
        assertThat(service.ownerKeyFromToken("o2.999999999999999999999999.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA.signature")).isEmpty();
        assertThat(service.ownerKeyFromToken("o9.123.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA.signature")).isEmpty();
    }

    @Test
    @DisplayName("legacy o1은 기본 정책에서 거부한다")
    void legacyOwnerToken_DefaultPolicyRejectsO1() {
        AccessSessionService service = serviceAt(NOW, Duration.ofDays(180), 0);
        String ownerKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        String legacy = legacyOwnerToken(ownerKey);

        assertThat(service.ownerKeyFromToken(legacy)).isEmpty();
    }

    @Test
    @DisplayName("legacy o1은 명시적 cutoff 이전에만 승격 대상으로 허용하고 cutoff 경계부터 거부한다")
    void legacyOwnerToken_ExplicitCutoffControlsMigration() {
        String ownerKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        String legacy = legacyOwnerToken(ownerKey);
        long cutoff = NOW.plusSeconds(120).getEpochSecond();

        AccessSessionService beforeCutoff = serviceAt(NOW.plusSeconds(119), Duration.ofDays(180), cutoff);
        AccessSessionService atCutoff = serviceAt(NOW.plusSeconds(120), Duration.ofDays(180), cutoff);

        assertThat(beforeCutoff.ownerKeyFromToken(legacy)).contains(ownerKey);
        assertThat(atCutoff.ownerKeyFromToken(legacy)).isEmpty();

        AccessSessionService.IssuedSession migrated = beforeCutoff.authenticate("TEST_PASSWORD", legacy);
        assertThat(migrated.ownerKey()).isEqualTo(ownerKey);
        assertThat(migrated.ownerToken()).startsWith("o2.");
    }

    @Test
    @DisplayName("기존 access 쿠키만 있는 보호 요청은 같은 owner key의 o2 owner 쿠키를 승격 발급한다")
    void interceptor_PromotesLegacyAccessTokenToCurrentOwnerCookie() throws Exception {
        AccessSessionService service = new AccessSessionService("TEST_PASSWORD", SECRET, 3600, false);
        String accessToken = service.authenticate("TEST_PASSWORD");
        String ownerKey = service.validateAndGetPrincipal(accessToken).ownerKey();
        AccessSessionInterceptor interceptor = new AccessSessionInterceptor(service);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .addInterceptors(interceptor)
                .build();

        mockMvc.perform(get("/protected")
                        .cookie(new Cookie(AccessSessionService.COOKIE_NAME, accessToken))
                        .header(AccessSessionInterceptor.CLIENT_HEADER, AccessSessionInterceptor.CLIENT_HEADER_VALUE))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString(AccessSessionService.OWNER_COOKIE_NAME + "=o2.")))
                .andExpect(header().string("Set-Cookie", containsString(service.issueOwnerToken(ownerKey).split("\\.")[0])));
    }

    private AccessSessionService serviceAt(Instant instant, Duration ownerTtl, long legacyCutoff) {
        return new AccessSessionService(
                "TEST_PASSWORD",
                SECRET,
                Duration.ofHours(1),
                ownerTtl,
                legacyCutoff,
                false,
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }

    private String legacyOwnerToken(String ownerKey) {
        String value = "o1." + ownerKey;
        String payload = "owner." + value;
        return value + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @org.springframework.web.bind.annotation.RestController
    static class TestController {
        @org.springframework.web.bind.annotation.GetMapping("/protected")
        org.springframework.http.ResponseEntity<Void> protectedEndpoint() {
            return org.springframework.http.ResponseEntity.noContent().build();
        }
    }
}
