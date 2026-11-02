package com.example.app.core.web.error;

/** An authentication failure (401, 403, 429). {@link AuthExceptionMapper} returns it as {@code AUTH_ERROR}. */
public class AuthException extends RuntimeException {

    /** The HTTP status of the response. */
    private final int status;

    /**
     * Creates an authentication failure; prefer the factory methods.
     *
     * @param status 401, 403 or 429, returned as is by {@code AuthExceptionMapper}
     * @param message the message for the client
     */
    public AuthException(int status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Returns the HTTP status of the response.
     *
     * @return 401, 403 or 429
     */
    public int getStatus() {
        return status;
    }

    /**
     * Creates the 429 for a login that found no free slot ({@code LoginGate}). The client should retry a little later.
     *
     * @return the exception
     */
    public static AuthException tooManyLogins() {
        return new AuthException(429, "Too many logins in progress. Please retry in a moment");
    }

    /**
     * Creates the 403 for a cookie-authenticated call without a matching {@code X-XSRF-TOKEN} header.
     *
     * @return the exception
     */
    public static AuthException csrfRejected() {
        return new AuthException(403, "Invalid CSRF token");
    }

    /**
     * Creates a 401 that does not say whether the user exists, the password was wrong, or the account is locked or
     * disabled.
     *
     * @return the exception
     */
    public static AuthException invalidCredentials() {
        return new AuthException(401, "Invalid user name or password");
    }
}
