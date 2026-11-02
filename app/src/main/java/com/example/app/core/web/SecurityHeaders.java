package com.example.app.core.web;

import java.util.Map;

/**
 * Values of the security headers added to every response.
 *
 * <ul>
 *   <li>JAX-RS responses: {@link SecurityHeadersFilter} adds them on every runtime.</li>
 *   <li>Static files (UI): Quarkus, Liberty and WildFly add them through each runtime's configuration. Payara and
 *       GlassFish cannot add headers to static files, so {@code index.html} and {@code login.html} declare the same
 *       policy in {@code <meta>} (except {@code frame-ancestors}), and {@code frame-guard.js} prevents display inside
 *       another site's frame. {@code SecurityHeadersTest} checks that the {@code <meta>} matches.</li>
 * </ul>
 */
public final class SecurityHeaders {

    /** No inline scripts or styles (the UI uses only external JS and CSS). {@code data:} is for the favicon. */
    public static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                    + "frame-ancestors 'none'; base-uri 'none'; form-action 'self'";

    /** Stops browsers from guessing a content type other than the declared one. */
    public static final String X_CONTENT_TYPE_OPTIONS = "nosniff";

    /** Sends no {@code Referer} to other pages, so URLs with IDs do not leak. */
    public static final String REFERRER_POLICY = "no-referrer";

    private static final Map<String, String> RESPONSE_HEADERS = Map.of(
            "Content-Security-Policy", CONTENT_SECURITY_POLICY,
            "X-Content-Type-Options", X_CONTENT_TYPE_OPTIONS,
            "Referrer-Policy", REFERRER_POLICY);

    private SecurityHeaders() {}

    /**
     * Returns the headers to add to every response.
     *
     * @return header name to value; immutable
     */
    public static Map<String, String> responseHeaders() {
        return RESPONSE_HEADERS;
    }
}
