package com.example.app.core.security;

import com.example.app.domain.BusinessTime;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Application settings. MicroProfile Config is not used (some runtimes lack it); each key is read from the system
 * property {@code app.<key>}, then the environment variable {@code APP_<KEY>} (for example
 * {@code app.import-dir} / {@code APP_IMPORT_DIR}). Values are never logged.
 */
@ApplicationScoped
public class SecuritySettings {

    private static final Logger LOG = Logger.getLogger(SecuritySettings.class.getName());

    /**
     * Minimum HMAC-SHA256 key length. A short key weakens the signature, so it stops startup as a configuration error.
     */
    private static final int MIN_SECRET_BYTES = 32;

    /** Whether the auth cookies get the {@code Secure} attribute. */
    public enum CookieSecure {
        /** Always. Use this in production. */
        ALWAYS,
        /** Only on requests that arrived over HTTPS ({@link #isSecureRequest}; the default). */
        AUTO,
        /** Never. Only for local development over HTTP. */
        NEVER
    }

    private Path importDir;
    private boolean demoUsers;
    private String adminPassword;
    private String userPassword;
    private CookieSecure cookieSecure;
    private boolean trustForwardedProto;
    private byte[] tokenSecret;
    private Duration accessTokenTtl;
    private boolean generatedSecret;

    /** Creates the settings. CDI creates the single instance; the values are read when it starts. */
    public SecuritySettings() {}

    @PostConstruct
    void init() {
        load(SecuritySettings::lookup);
        LOG.info(() -> "import directory: " + importDir.toAbsolutePath().normalize());
        LOG.info(() -> "business time zone: " + BusinessTime.zone());
        // Warn here rather than in load(), because the unit tests call load() many times.
        if (generatedSecret) {
            LOG.warning("app.token-secret is not set: access tokens are signed with a random secret and "
                    + "become invalid after a restart. Set APP_TOKEN_SECRET in production.");
        }
        if (demoUsers) {
            LOG.warning("app.demo-users=true: demo accounts admin/admin and user/user are enabled. "
                    + "Do not use this setting in production.");
        }
    }

    /** Reads the settings from {@code source} (which returns the value for a key such as {@code import-dir}). */
    void load(Function<String, Optional<String>> source) {
        importDir = Path.of(source.apply("import-dir").orElse("data/import"));
        demoUsers = source.apply("demo-users").map(Boolean::parseBoolean).orElse(false);
        adminPassword = source.apply("admin-password").orElse(null);
        userPassword = source.apply("user-password").orElse(null);
        String secure = source.apply("cookie-secure").orElse("auto");
        try {
            cookieSecure = CookieSecure.valueOf(secure.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("app.cookie-secure must be always, auto or never: " + secure);
        }
        trustForwardedProto =
                source.apply("trust-forwarded-proto").map(Boolean::parseBoolean).orElse(false);
        Optional<String> configuredSecret = source.apply("token-secret");
        generatedSecret = configuredSecret.isEmpty();
        tokenSecret = configuredSecret
                .map(value -> value.getBytes(StandardCharsets.UTF_8))
                .orElseGet(SecuritySettings::randomSecret);
        if (tokenSecret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.token-secret must be at least " + MIN_SECRET_BYTES
                    + " bytes for HMAC-SHA256; got " + tokenSecret.length);
        }
        accessTokenTtl = Duration.ofSeconds(
                Long.parseLong(source.apply("access-token-ttl-seconds").orElse("600")));
    }

    private static byte[] randomSecret() {
        byte[] generated = new byte[32];
        new SecureRandom().nextBytes(generated);
        return generated;
    }

    /**
     * Returns the signing key of access tokens.
     *
     * <p>To keep tokens valid across restarts and accept them on several nodes, set {@code app.token-secret} / {@code
     * APP_TOKEN_SECRET}. If unset, a random key is made per start.
     *
     * @return the key, at least 32 bytes
     */
    public byte[] tokenSecret() {
        return tokenSecret.clone();
    }

    /**
     * Returns the lifetime of an access token. It cannot be revoked, so it is short (600 s by default).
     *
     * @return the lifetime
     */
    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    /**
     * Returns the directory the CSV import may read.
     *
     * @return the directory, {@code data/import} by default
     */
    public Path importDir() {
        return importDir;
    }

    /**
     * Returns the password of an account created at startup: the configured one if set, the user name if demo users are
     * enabled.
     *
     * @param username {@code admin} or {@code user}
     * @return the password, or empty if the account is not to be created
     * @throws IllegalArgumentException if {@code username} is not {@code admin} or {@code user}
     */
    public Optional<String> initialPassword(String username) {
        String configured = switch (username) {
            case "admin" -> adminPassword;
            case "user" -> userPassword;
            default -> throw new IllegalArgumentException("not a seeded account: " + username);
        };
        if (configured != null) {
            return Optional.of(configured);
        }
        return demoUsers ? Optional.of(username) : Optional.empty();
    }

    /**
     * Tells whether the request arrived over HTTPS.
     *
     * <p>{@code X-Forwarded-Proto} is trusted only with {@code app.trust-forwarded-proto=true}: without a
     * TLS-terminating proxy in front, any client can send it.
     *
     * @param requestUri the request URI
     * @param forwardedProto the {@code X-Forwarded-Proto} header, or null
     * @return true if the request counts as HTTPS
     */
    public boolean isSecureRequest(URI requestUri, String forwardedProto) {
        if (trustForwardedProto && forwardedProto != null && !forwardedProto.isBlank()) {
            return "https".equalsIgnoreCase(forwardedProto.trim());
        }
        return "https".equalsIgnoreCase(requestUri.getScheme());
    }

    /**
     * Tells whether the auth cookies set in the response to this request get the {@code Secure} attribute.
     *
     * @param requestUri the request URI
     * @param forwardedProto the {@code X-Forwarded-Proto} header, or null
     * @return true for {@code always}, false for {@code never}, {@link #isSecureRequest} for {@code auto}
     */
    public boolean secureCookie(URI requestUri, String forwardedProto) {
        return switch (cookieSecure) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> isSecureRequest(requestUri, forwardedProto);
        };
    }

    private static Optional<String> lookup(String key) {
        String value = System.getProperty("app." + key);
        if (value == null || value.isBlank()) {
            value = System.getenv("APP_" + key.replace('-', '_').toUpperCase(Locale.ROOT));
        }
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }
}
