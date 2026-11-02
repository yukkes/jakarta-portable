package com.example.app.core.web.auth;

import com.example.app.api.model.AuthStatusResponse;
import com.example.app.api.model.LoginRequest;
import com.example.app.api.model.LoginResponse;
import com.example.app.api.model.UserResponse;
import com.example.app.core.security.LoginGate;
import com.example.app.core.security.SecuritySettings;
import com.example.app.core.service.AuthService;
import com.example.app.core.service.AuthService.AuthSession;
import com.example.app.core.service.AuthService.LoginResult;
import com.example.app.core.web.error.AuthException;
import com.example.app.core.web.error.ProblemDetail;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;

/**
 * The authentication endpoints. Browsers log in with {@code /session} (tokens arrive as HttpOnly cookies),
 * Bearer clients with {@code /login} (tokens arrive in the body).
 */
@Path("/auth")
@Produces({MediaType.APPLICATION_JSON, ProblemDetail.MEDIA_TYPE})
public class AuthResource {

    @Inject
    private AuthService authService;

    @Inject
    private LoginGate loginGate;

    @Inject
    private SecuritySettings settings;

    @Context
    private UriInfo uriInfo;

    @Context
    private HttpHeaders headers;

    /** Creates the resource. The runtime creates one per request and injects its fields. */
    public AuthResource() {}

    /**
     * Logs a browser in. The tokens go into HttpOnly cookies; the body carries only the user name and roles.
     *
     * @param request the user name and password
     * @return 200 with the user and the session, refresh and CSRF cookies
     * @throws AuthException if the credentials are wrong or the account is locked or disabled (401), or no login slot
     *     frees up in time (429)
     */
    @POST
    @Path("/session")
    @PublicEndpoint
    @Consumes(MediaType.APPLICATION_JSON)
    public Response session(@Valid LoginRequest request) {
        return AuthResponses.session(
                loginGate.run(() -> authService.login(request.getUsername(), request.getPassword())),
                settings,
                headers,
                uriInfo);
    }

    /**
     * Logs a Bearer client in.
     *
     * @param request the user name and password
     * @return the access token, the refresh token, the expiry and the user
     * @throws AuthException if the credentials are wrong or the account is locked or disabled (401), or no login slot
     *     frees up in time (429)
     */
    @POST
    @Path("/login")
    @PublicEndpoint
    @Consumes(MediaType.APPLICATION_JSON)
    public LoginResponse login(@Valid LoginRequest request) {
        return AuthResponses.tokens(
                loginGate.run(() -> authService.login(request.getUsername(), request.getPassword())));
    }

    /**
     * Exchanges the refresh token for a new session and rotates it. This is the only call that reads the session from
     * the database.
     *
     * <p>It is a {@code @PublicEndpoint} (by now the access token has often expired), so it does not pass {@code
     * CsrfFilter}; the CSRF double-submit check is done here instead.
     *
     * @return 200 with the user and new cookies; the old refresh token no longer works
     * @throws AuthException if the refresh cookie is missing, unknown, expired or already used (401), or the CSRF
     *     header does not match (403)
     */
    @POST
    @Path("/refresh")
    @PublicEndpoint
    public Response refresh() {
        String refreshToken = AuthTokens.cookie(headers, AuthTokens.REFRESH_COOKIE);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw AuthException.invalidCredentials();
        }
        if (!AuthTokens.csrfHeaderMatchesCookie(headers.getRequestHeaders())) {
            throw AuthException.csrfRejected();
        }
        LoginResult result = authService.refresh(refreshToken).orElseThrow(AuthException::invalidCredentials);
        return AuthResponses.session(result, settings, headers, uriInfo);
    }

    /**
     * Returns the logged-in user's name and roles, from the access token (no database access).
     *
     * @param securityContext the context set by {@code AuthFilter}
     * @return the user
     */
    @GET
    @Path("/me")
    @RequireRole({"USER", "ADMIN"})
    public UserResponse me(@Context SecurityContext securityContext) {
        return AuthResponses.user(securityContext);
    }

    /**
     * Tells whether the caller is logged in, and as whom. It never answers 401 for a missing token, so the UI can call
     * it before deciding where to go.
     *
     * @param securityContext the context set by {@code AuthFilter}, without a principal for anonymous calls
     * @return {@code authenticated} with the user name and roles, or {@code false} and no roles
     */
    @GET
    @Path("/status")
    @OptionalAuth
    public AuthStatusResponse status(@Context SecurityContext securityContext) {
        AuthStatusResponse response = new AuthStatusResponse();
        boolean authenticated = securityContext.getUserPrincipal() != null;
        response.setAuthenticated(authenticated);
        if (authenticated) {
            response.setUsername(securityContext.getUserPrincipal().getName());
            response.setRoles(AuthResponses.roles(securityContext));
        } else {
            response.setRoles(List.of());
        }
        return response;
    }

    /**
     * Ends the session: deletes it and its refresh token from the database and expires the cookies.
     *
     * <p>An access token already handed out stays valid until it expires (at most {@code
     * app.access-token-ttl-seconds}).
     *
     * @return 204 with expired cookies
     * @throws AuthException if the access token is invalid (401)
     */
    @POST
    @Path("/logout")
    @RequireRole({"USER", "ADMIN"})
    public Response logout() {
        // The filter already verified it, but how a resource reads request properties differs between runtimes,
        // so verify the token again to get the session.
        AuthSession session = AuthTokens.extract(headers)
                .flatMap(source -> authService.authenticate(source.token()))
                .orElseThrow(AuthException::invalidCredentials);
        authService.logout(session, AuthTokens.cookie(headers, AuthTokens.REFRESH_COOKIE));
        return AuthResponses.loggedOut(settings, headers, uriInfo);
    }
}
