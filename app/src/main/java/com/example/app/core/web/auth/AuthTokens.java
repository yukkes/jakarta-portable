package com.example.app.core.web.auth;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/** Names of the cookies and headers used for authentication, and extracting the token from a request. */
public final class AuthTokens {

    /** The cookie that carries the access token for browsers (HttpOnly). */
    public static final String SESSION_COOKIE = "APP_SESSION";
    /** The refresh token. Sent only to {@code /api/auth}. */
    public static final String REFRESH_COOKIE = "APP_REFRESH";

    /** The only path the refresh-token cookie is sent to. */
    public static final String REFRESH_COOKIE_PATH = "/api/auth";
    /** The cookie with the CSRF token. Readable by the page's script, which copies it into {@link #CSRF_HEADER}. */
    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    /** The header that must repeat {@link #CSRF_COOKIE} on cookie-authenticated writes. */
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private AuthTokens() {}

    /**
     * Finds the access token of a request: the {@code Authorization: Bearer} header first, then the session cookie. The
     * token is not verified.
     *
     * @param headers the request headers
     * @return the token and where it came from, or empty if neither is present
     */
    public static Optional<TokenSource> extract(HttpHeaders headers) {
        return extract(headers.getRequestHeaders());
    }

    /**
     * Finds the access token of a request, like {@link #extract(HttpHeaders)}, from the raw headers a filter sees.
     *
     * @param headers the raw request headers
     * @return the token and where it came from, or empty if neither is present
     */
    public static Optional<TokenSource> extract(MultivaluedMap<String, String> headers) {
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).trim();
            if (!token.isEmpty()) {
                return Optional.of(new TokenSource(token, false));
            }
        }

        String cookie = cookie(headers, SESSION_COOKIE);
        if (cookie != null && !cookie.isBlank()) {
            return Optional.of(new TokenSource(cookie, true));
        }
        return Optional.empty();
    }

    /**
     * Reads a cookie from the {@code Cookie} header.
     *
     * @param headers the request headers
     * @param name the cookie name
     * @return the value of the first cookie with that name, or null
     */
    public static String cookie(HttpHeaders headers, String name) {
        return cookie(headers.getRequestHeaders(), name);
    }

    /**
     * Reads a cookie, like {@link #cookie(HttpHeaders, String)}, from the raw headers a filter sees.
     *
     * @param headers the raw request headers
     * @param name the cookie name
     * @return the value of the first cookie with that name, or null
     */
    public static String cookie(MultivaluedMap<String, String> headers, String name) {
        String cookieHeader = headers.getFirst(HttpHeaders.COOKIE);
        if (cookieHeader == null) {
            return null;
        }
        for (String part : cookieHeader.split(";")) {
            String candidate = part.trim();
            int separator = candidate.indexOf('=');
            if (separator > 0 && candidate.substring(0, separator).trim().equals(name)) {
                return candidate.substring(separator + 1).trim();
            }
        }
        return null;
    }

    /**
     * Performs the CSRF double-submit check.
     *
     * <p>Another site's page can make the browser send the cookie, but cannot read its value and copy it into a header.
     *
     * @param headers the raw request headers
     * @return true if an {@code X-XSRF-TOKEN} header exists and equals the {@code XSRF-TOKEN} cookie
     */
    public static boolean csrfHeaderMatchesCookie(MultivaluedMap<String, String> headers) {
        String header = headers.getFirst(CSRF_HEADER);
        String cookie = cookie(headers, CSRF_COOKIE);
        return header != null && cookie != null && !header.isBlank() && sameToken(header, cookie);
    }

    /**
     * Compares two tokens in constant time, so the length of the match does not show in the response time.
     *
     * @param left one token
     * @param right the other token
     * @return true if they are equal
     */
    public static boolean sameToken(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A token found in a request.
     *
     * @param token the token as sent
     * @param cookie true if it came from the session cookie, false if from the {@code Authorization} header
     */
    public record TokenSource(String token, boolean cookie) {}
}
