package com.example.app.core.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Creating and verifying tokens.
 *
 * <p>The access token is an HS256 JWT, built with JSON-P and signed with HMAC-SHA256 (no JOSE
 * library). It carries everything authorization needs (user, roles, CSRF token, session ID), so
 * verifying it needs no DB.
 */
@ApplicationScoped
public class TokenService {

    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();
    private static final String HMAC = "HmacSHA256";

    private final SecureRandom random = new SecureRandom();

    /**
     * The JSON-P provider. Every {@code Json.createXxx} call looks the provider up with the service loader,
     * which alone costs 20-40% of throughput, so it is resolved once and reused (docs/performance.md).
     */
    private final JsonProvider json = JsonProvider.provider();

    /** Creates the service. CDI creates the single instance (it is application-scoped). */
    public TokenService() {}

    /**
     * Creates an unguessable random value, used for refresh tokens.
     *
     * @return 32 random bytes, Base64url-encoded
     */
    public String newToken() {
        return randomToken(32);
    }

    /**
     * Creates a random value for the CSRF double-submit check; it goes into the access token and the {@code XSRF-TOKEN}
     * cookie.
     *
     * @return 24 random bytes, Base64url-encoded
     */
    public String newCsrfToken() {
        return randomToken(24);
    }

    /**
     * Hashes a refresh token. The database stores only this value.
     *
     * @param token the refresh token
     * @return its SHA-256, Base64url-encoded
     */
    public String hash(String token) {
        try {
            return BASE64.encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /**
     * Issues an access token (an HS256 JWT).
     *
     * @param secret the signing key
     * @param userId the user ID ({@code sub})
     * @param username the user name
     * @param roles the user's roles
     * @param sessionId the refresh-token row the token belongs to ({@code sid})
     * @param csrfToken the CSRF token of the session
     * @param issuedAt the issue time ({@code iat})
     * @param expiresAt the expiry ({@code exp})
     * @return the signed token
     */
    public String accessToken(
            byte[] secret,
            long userId,
            String username,
            Set<String> roles,
            long sessionId,
            String csrfToken,
            Instant issuedAt,
            Instant expiresAt) {
        JsonObject header =
                json.createObjectBuilder().add("alg", "HS256").add("typ", "JWT").build();
        JsonObject claims = json.createObjectBuilder()
                .add("sub", userId)
                .add("name", username)
                .add("roles", json.createArrayBuilder(roles))
                .add("sid", sessionId)
                .add("csrf", csrfToken)
                .add("iat", issuedAt.getEpochSecond())
                .add("exp", expiresAt.getEpochSecond())
                .build();
        String signingInput = encode(header.toString()) + "." + encode(claims.toString());
        return signingInput + "." + BASE64.encodeToString(hmac(secret, signingInput));
    }

    /**
     * Verifies the signature and expiry of an access token and returns its contents.
     *
     * @param secret the signing key
     * @param token the token as sent, or null
     * @return empty if the signature is wrong, the token has expired or it is malformed
     */
    public Optional<AccessToken> parseAccessToken(byte[] secret, String token) {
        if (token == null) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            byte[] presented = BASE64_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(hmac(secret, parts[0] + "." + parts[1]), presented)) {
                return Optional.empty();
            }
            // Even with a valid signature, never trust the algorithm the client specified.
            if (!"HS256".equals(decode(parts[0]).getString("alg", ""))) {
                return Optional.empty();
            }
            JsonObject claims = decode(parts[1]);
            long expires = claims.getJsonNumber("exp").longValue();
            if (expires <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(new AccessToken(
                    claims.getJsonNumber("sub").longValue(),
                    claims.getString("name"),
                    roles(claims.getJsonArray("roles")),
                    claims.getJsonNumber("sid").longValue(),
                    claims.getString("csrf"),
                    Instant.ofEpochSecond(expires)));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    private static Set<String> roles(JsonArray array) {
        Set<String> roles = new LinkedHashSet<>();
        if (array != null) {
            for (JsonValue value : array) {
                if (value instanceof JsonString string) {
                    roles.add(string.getString());
                }
            }
        }
        return Set.copyOf(roles);
    }

    private JsonObject decode(String encoded) {
        String value = new String(BASE64_DECODER.decode(encoded), StandardCharsets.UTF_8);
        try (JsonReader reader = json.createReader(new StringReader(value))) {
            return reader.readObject();
        }
    }

    private static byte[] hmac(byte[] secret, String value) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is not available", exception);
        }
    }

    private static String encode(String value) {
        return BASE64.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return BASE64.encodeToString(value);
    }

    /**
     * The contents of a verified access token.
     *
     * @param userId the user ID
     * @param username the user name
     * @param roles the user's roles
     * @param sessionId the session (refresh-token row) ID
     * @param csrfToken the CSRF token of the session
     * @param expiresAt the expiry
     */
    public record AccessToken(
            long userId, String username, Set<String> roles, long sessionId, String csrfToken, Instant expiresAt) {}
}
