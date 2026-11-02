package com.example.app.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SecuritySettingsTest {

    private static final URI HTTP = URI.create("http://localhost:8080/api/auth/session");
    private static final URI HTTPS = URI.create("https://example.com/api/auth/session");

    @Test
    void createsNoUsersByDefault() {
        SecuritySettings settings = load(Map.of());

        assertTrue(settings.initialPassword("admin").isEmpty());
        assertTrue(settings.initialPassword("user").isEmpty());
        assertEquals(Path.of("data/import"), settings.importDir());
    }

    @Test
    void demoUsersUseWellKnownPasswords() {
        SecuritySettings settings = load(Map.of("demo-users", "true"));

        assertEquals(Optional.of("admin"), settings.initialPassword("admin"));
        assertEquals(Optional.of("user"), settings.initialPassword("user"));
    }

    @Test
    void configuredPasswordWinsOverDemoUsers() {
        SecuritySettings settings = load(Map.of("demo-users", "true", "admin-password", "s3cret"));

        assertEquals(Optional.of("s3cret"), settings.initialPassword("admin"));
        assertEquals(Optional.of("user"), settings.initialPassword("user"));
        assertThrows(IllegalArgumentException.class, () -> settings.initialPassword("someone-else"));
    }

    @Test
    void forwardedProtoIsIgnoredUnlessTrusted() {
        SecuritySettings settings = load(Map.of());

        assertFalse(settings.isSecureRequest(HTTP, "https"));
        assertTrue(settings.isSecureRequest(HTTPS, null));
        assertFalse(settings.secureCookie(HTTP, "https"));
    }

    @Test
    void trustedForwardedProtoDecides() {
        SecuritySettings settings = load(Map.of("trust-forwarded-proto", "true"));

        assertTrue(settings.isSecureRequest(HTTP, "https"));
        assertFalse(settings.isSecureRequest(HTTPS, "http"));
        assertTrue(settings.isSecureRequest(HTTPS, null));
        assertTrue(settings.secureCookie(HTTP, "HTTPS"));
    }

    @Test
    void cookieSecureOverridesTheRequest() {
        assertTrue(load(Map.of("cookie-secure", "always")).secureCookie(HTTP, null));
        assertFalse(load(Map.of("cookie-secure", "NEVER")).secureCookie(HTTPS, null));
        assertThrows(IllegalStateException.class, () -> load(Map.of("cookie-secure", "sometimes")));
    }

    @Test
    void generatesAStrongSecretWhenNoneIsConfigured() {
        assertEquals(32, load(Map.of()).tokenSecret().length);
    }

    @Test
    void rejectsASecretWeakerThanTheHmacOutput() {
        // HMAC itself does not reject short keys, so anything under 32 bytes fails at startup.
        assertThrows(IllegalStateException.class, () -> load(Map.of("token-secret", "secret")));
        assertThrows(IllegalStateException.class, () -> load(Map.of("token-secret", "x".repeat(31))));
        assertEquals(32, load(Map.of("token-secret", "x".repeat(32))).tokenSecret().length);
    }

    private SecuritySettings load(Map<String, String> values) {
        SecuritySettings settings = new SecuritySettings();
        settings.load(key -> Optional.ofNullable(values.get(key)));
        return settings;
    }
}
