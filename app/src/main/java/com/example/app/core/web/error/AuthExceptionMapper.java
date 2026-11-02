package com.example.app.core.web.error;

import jakarta.ws.rs.ext.Provider;

/** Turns an {@link AuthException} into a response. */
@Provider
public class AuthExceptionMapper extends ApiExceptionMapper<AuthException> {

    /** Creates the mapper. The runtime discovers it through {@code @Provider} and injects its fields. */
    public AuthExceptionMapper() {}

    @Override
    protected Outcome classify(AuthException exception) {
        return Outcome.of(exception.getStatus(), "AUTH_ERROR", exception.getMessage());
    }
}
