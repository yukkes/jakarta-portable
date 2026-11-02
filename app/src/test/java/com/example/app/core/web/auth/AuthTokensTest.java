package com.example.app.core.web.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;

class AuthTokensTest {

    @Test
    void acceptsAHeaderThatEchoesTheCsrfCookie() {
        assertTrue(AuthTokens.csrfHeaderMatchesCookie(headers("APP_REFRESH=r; XSRF-TOKEN=abc", "abc")));
    }

    @Test
    void rejectsAMissingOrDifferentHeader() {
        // A form post from another site: the browser attaches only the cookie, not the header.
        assertFalse(AuthTokens.csrfHeaderMatchesCookie(headers("APP_REFRESH=r; XSRF-TOKEN=abc", null)));
        assertFalse(AuthTokens.csrfHeaderMatchesCookie(headers("APP_REFRESH=r; XSRF-TOKEN=abc", "abd")));
        assertFalse(AuthTokens.csrfHeaderMatchesCookie(headers("APP_REFRESH=r", "abc")));
        assertFalse(AuthTokens.csrfHeaderMatchesCookie(headers("XSRF-TOKEN=", "")));
    }

    private static MultivaluedMap<String, String> headers(String cookie, String csrfHeader) {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.putSingle(HttpHeaders.COOKIE, cookie);
        if (csrfHeader != null) {
            headers.putSingle(AuthTokens.CSRF_HEADER, csrfHeader);
        }
        return headers;
    }
}
