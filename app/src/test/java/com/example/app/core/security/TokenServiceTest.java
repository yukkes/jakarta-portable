package com.example.app.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.app.core.security.TokenService.AccessToken;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * The access token is signed by hand, so the cases it must reject are checked here, not only through HTTP status codes.
 */
class TokenServiceTest {

    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OTHER_SECRET = "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8);
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    private final TokenService tokens = new TokenService();

    @Test
    void roundTripsTheClaimsItIssued() {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(600);
        String token = tokens.accessToken(
                SECRET, 42L, "admin", new LinkedHashSet<>(Set.of("ADMIN")), 7L, "csrf-value", issuedAt, expiresAt);

        AccessToken parsed = tokens.parseAccessToken(SECRET, token).orElseThrow();

        assertEquals(42L, parsed.userId());
        assertEquals("admin", parsed.username());
        assertEquals(Set.of("ADMIN"), parsed.roles());
        assertEquals(7L, parsed.sessionId());
        assertEquals("csrf-value", parsed.csrfToken());
        assertEquals(expiresAt.getEpochSecond(), parsed.expiresAt().getEpochSecond());
    }

    @Test
    void rejectsATokenSignedWithAnotherSecret() {
        String token = tokens.accessToken(
                OTHER_SECRET,
                1L,
                "admin",
                Set.of("ADMIN"),
                1L,
                "csrf",
                Instant.now(),
                Instant.now().plusSeconds(600));

        assertTrue(tokens.parseAccessToken(SECRET, token).isEmpty());
    }

    /** After the key changes, verification uses the new key, even on the same thread (key rotation works). */
    @Test
    void usesTheKeyItIsGivenAfterAnotherKeyWasUsedOnTheSameThread() {
        String withSecret = tokens.accessToken(
                SECRET,
                1L,
                "admin",
                Set.of("ADMIN"),
                1L,
                "csrf",
                Instant.now(),
                Instant.now().plusSeconds(600));
        String withOther = tokens.accessToken(
                OTHER_SECRET,
                1L,
                "admin",
                Set.of("ADMIN"),
                1L,
                "csrf",
                Instant.now(),
                Instant.now().plusSeconds(600));

        assertTrue(tokens.parseAccessToken(SECRET, withOther).isEmpty(), "the other key was accepted");
        assertTrue(tokens.parseAccessToken(OTHER_SECRET, withSecret).isEmpty(), "the first key was reused");
        assertTrue(tokens.parseAccessToken(SECRET, withSecret).isPresent());
        assertTrue(tokens.parseAccessToken(OTHER_SECRET, withOther).isPresent());
    }

    @Test
    void rejectsTamperedClaims() {
        String token = tokens.accessToken(
                SECRET,
                1L,
                "user",
                Set.of("USER"),
                1L,
                "csrf",
                Instant.now(),
                Instant.now().plusSeconds(600));
        String[] parts = token.split("\\.");
        // A token with the header and signature kept but the body changed (a privilege escalation attempt) is rejected.
        String escalated = BASE64.encodeToString(("{\"sub\":1,\"name\":\"user\",\"roles\":[\"ADMIN\"],"
                        + "\"sid\":1,\"csrf\":\"csrf\",\"iat\":"
                        + Instant.now().getEpochSecond() + ",\"exp\":"
                        + Instant.now().plusSeconds(600).getEpochSecond() + "}")
                .getBytes(StandardCharsets.UTF_8));

        assertTrue(tokens.parseAccessToken(SECRET, parts[0] + "." + escalated + "." + parts[2])
                .isEmpty());
    }

    /**
     * The JWT algorithm confusion attack. The signature is valid for the rewritten header, so only the explicit
     * {@code alg} check can reject it.
     */
    @Test
    void rejectsAValidlySignedTokenWithAnotherAlgorithm() {
        for (String algorithm : new String[] {"none", "HS512", "RS256", ""}) {
            String header = BASE64.encodeToString(
                    ("{\"alg\":\"" + algorithm + "\",\"typ\":\"JWT\"}").getBytes(StandardCharsets.UTF_8));
            String claims = BASE64.encodeToString(("{\"sub\":1,\"name\":\"admin\",\"roles\":[\"ADMIN\"],"
                            + "\"sid\":1,\"csrf\":\"csrf\",\"exp\":"
                            + Instant.now().plusSeconds(600).getEpochSecond() + "}")
                    .getBytes(StandardCharsets.UTF_8));
            String signingInput = header + "." + claims;

            String token = signingInput + "." + BASE64.encodeToString(sign(SECRET, signingInput));

            assertTrue(tokens.parseAccessToken(SECRET, token).isEmpty(), "alg=" + algorithm + " was accepted");
        }
    }

    @Test
    void rejectsAnExpiredToken() {
        Instant now = Instant.now();
        String token = tokens.accessToken(
                SECRET, 1L, "admin", Set.of("ADMIN"), 1L, "csrf", now.minusSeconds(1200), now.minusSeconds(600));

        assertTrue(tokens.parseAccessToken(SECRET, token).isEmpty());
    }

    @Test
    void rejectsMalformedTokens() {
        assertTrue(tokens.parseAccessToken(SECRET, null).isEmpty());
        assertTrue(tokens.parseAccessToken(SECRET, "").isEmpty());
        assertTrue(tokens.parseAccessToken(SECRET, "only.two").isEmpty());
        assertTrue(tokens.parseAccessToken(SECRET, "a.b.c.d").isEmpty());
        assertTrue(tokens.parseAccessToken(SECRET, "not-base64%.not-base64%.not-base64%")
                .isEmpty());

        // The signature is valid but the body is not JSON. The parse error must not leak out.
        String header = BASE64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String claims = BASE64.encodeToString("not json".getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;
        String token = signingInput + "." + BASE64.encodeToString(sign(SECRET, signingInput));

        assertTrue(tokens.parseAccessToken(SECRET, token).isEmpty());
    }

    @Test
    void rejectsATokenWithoutExpiry() {
        String header = BASE64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String claims = BASE64.encodeToString("{\"sub\":1,\"name\":\"admin\",\"roles\":[\"ADMIN\"],\"sid\":1,"
                .concat("\"csrf\":\"csrf\"}")
                .getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;

        String token = signingInput + "." + BASE64.encodeToString(sign(SECRET, signingInput));

        assertTrue(tokens.parseAccessToken(SECRET, token).isEmpty());
    }

    @Test
    void refreshTokensAreOpaqueAndDistinct() {
        String first = tokens.newToken();

        assertNotEquals(first, tokens.newToken());
        assertEquals(32, Base64.getUrlDecoder().decode(first).length);
        assertEquals(24, Base64.getUrlDecoder().decode(tokens.newCsrfToken()).length);
    }

    @Test
    void hashesRefreshTokensWithoutKeepingTheValue() {
        String token = tokens.newToken();
        Optional<String> hash = Optional.of(tokens.hash(token));

        assertEquals(hash.orElseThrow(), tokens.hash(token));
        assertNotEquals(token, hash.orElseThrow());
        assertFalse(hash.orElseThrow().contains(token));
    }

    private static byte[] sign(byte[] secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
