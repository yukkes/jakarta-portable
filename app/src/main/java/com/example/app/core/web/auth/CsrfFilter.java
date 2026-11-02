package com.example.app.core.web.auth;

import com.example.app.core.service.AuthService.AuthSession;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Requires a CSRF token on cookie-authenticated writes (POST, PUT, PATCH, DELETE). 403 unless the header, the cookie
 * and the value in the access token all match. Calls with a Bearer header are exempt.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class CsrfFilter implements ContainerRequestFilter {

    /** Creates the filter. The runtime discovers it through {@code @Provider}. */
    public CsrfFilter() {}

    @Override
    public void filter(ContainerRequestContext request) {
        if (!isStateChanging(request.getMethod())) {
            return;
        }
        if (!Boolean.TRUE.equals(request.getProperty("auth.cookie"))) {
            return;
        }

        AuthSession session = (AuthSession) request.getProperty("auth.session");
        if (session == null) {
            return;
        }

        if (!AuthTokens.csrfHeaderMatchesCookie(request.getHeaders())
                || !AuthTokens.sameToken(request.getHeaders().getFirst(AuthTokens.CSRF_HEADER), session.csrfToken())) {
            request.abortWith(AuthResponses.forbidden(request, "Invalid CSRF token"));
        }
    }

    private boolean isStateChanging(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }
}
