package com.example.app.core.web.auth;

import com.example.app.api.model.LoginResponse;
import com.example.app.api.model.UserResponse;
import com.example.app.core.security.SecuritySettings;
import com.example.app.core.service.AuthService.LoginResult;
import com.example.app.core.web.error.ProblemDetail;
import com.example.app.domain.BusinessTime;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Builds the responses of the auth endpoints and filters (cookies, DTOs, error bodies). */
public final class AuthResponses {

    private AuthResponses() {}

    /**
     * Builds a 401 for a filter that aborts the request, in the format the request asked for.
     *
     * @param request the request being aborted
     * @param message the message for the client
     * @return the response
     */
    public static Response unauthorized(ContainerRequestContext request, String message) {
        return error(request, 401, "UNAUTHORIZED", message);
    }

    /**
     * Builds a 403 for a filter that aborts the request, in the format the request asked for.
     *
     * @param request the request being aborted
     * @param message the message for the client
     * @return the response
     */
    public static Response forbidden(ContainerRequestContext request, String message) {
        return error(request, 403, "FORBIDDEN", message);
    }

    /**
     * Builds the login or refresh response for browsers. The tokens travel in HttpOnly cookies, never in the body.
     *
     * <p>The refresh-token and CSRF-token cookies last until the session's absolute expiry; the session cookie as long
     * as the access token.
     *
     * @param result the issued session
     * @param settings decides the {@code Secure} attribute
     * @param headers the request headers ({@code X-Forwarded-Proto})
     * @param uriInfo the request URI (its scheme)
     * @return 200 with the user in the body and the three cookies
     */
    public static Response session(
            LoginResult result, SecuritySettings settings, HttpHeaders headers, UriInfo uriInfo) {
        Cookies cookies = new Cookies(settings, headers, uriInfo);
        int accessMaxAge = (int) Math.max(settings.accessTokenTtl().toSeconds(), 1);
        int sessionMaxAge = (int) Math.max(
                Duration.between(BusinessTime.now(), result.sessionExpiresAt()).toSeconds(), 1);
        return Response.ok(user(result))
                .cookie(
                        cookies.of(AuthTokens.SESSION_COOKIE, result.accessToken(), accessMaxAge, "/", true),
                        cookies.of(AuthTokens.CSRF_COOKIE, result.csrfToken(), sessionMaxAge, "/", false),
                        cookies.of(
                                AuthTokens.REFRESH_COOKIE,
                                result.refreshToken(),
                                sessionMaxAge,
                                AuthTokens.REFRESH_COOKIE_PATH,
                                true))
                .build();
    }

    /**
     * Builds a response that expires every auth cookie.
     *
     * @param settings decides the {@code Secure} attribute
     * @param headers the request headers ({@code X-Forwarded-Proto})
     * @param uriInfo the request URI (its scheme)
     * @return 204 with the cookies emptied and expired
     */
    public static Response loggedOut(SecuritySettings settings, HttpHeaders headers, UriInfo uriInfo) {
        Cookies cookies = new Cookies(settings, headers, uriInfo);
        return Response.noContent()
                .cookie(
                        cookies.of(AuthTokens.SESSION_COOKIE, "", 0, "/", true),
                        cookies.of(AuthTokens.CSRF_COOKIE, "", 0, "/", false),
                        cookies.of(AuthTokens.REFRESH_COOKIE, "", 0, AuthTokens.REFRESH_COOKIE_PATH, true))
                .build();
    }

    /**
     * Builds the login response for Bearer clients, with the tokens in the body.
     *
     * @param result the issued session
     * @return the tokens, their expiry and the user
     */
    public static LoginResponse tokens(LoginResult result) {
        LoginResponse response = new LoginResponse();
        response.setToken(result.accessToken());
        response.setRefreshToken(result.refreshToken());
        response.setExpiresAt(result.expiresAt());
        response.setUsername(result.user().username());
        response.setRoles(List.copyOf(result.user().roles()));
        return response;
    }

    /**
     * Extracts the user of a login result, without any token.
     *
     * @param result the issued session
     * @return the user name and roles
     */
    public static UserResponse user(LoginResult result) {
        return user(result.user().username(), List.copyOf(result.user().roles()));
    }

    /**
     * Extracts the user of a verified request.
     *
     * @param securityContext the context of an authenticated request; its principal must not be null
     * @return the user name and roles
     */
    public static UserResponse user(SecurityContext securityContext) {
        return user(securityContext.getUserPrincipal().getName(), roles(securityContext));
    }

    /**
     * Lists the application roles the caller has, ADMIN first.
     *
     * <p>{@code SecurityContext} can only be asked role by role, so the known roles are tested one by one; a new role
     * has to be added here.
     *
     * @param securityContext the request's security context
     * @return the roles, possibly empty
     */
    public static List<String> roles(SecurityContext securityContext) {
        List<String> roles = new ArrayList<>();
        if (securityContext.isUserInRole("ADMIN")) {
            roles.add("ADMIN");
        }
        if (securityContext.isUserInRole("USER")) {
            roles.add("USER");
        }
        return roles;
    }

    private static UserResponse user(String username, List<String> roles) {
        UserResponse response = new UserResponse();
        response.setUsername(username);
        response.setRoles(roles);
        return response;
    }

    /**
     * Builds an error response from a filter, in the same format as the exception mappers.
     *
     * @param request the request being aborted
     * @param status the HTTP status
     * @param code the error code of the default format
     * @param message the message for the client
     * @return the response
     */
    private static Response error(ContainerRequestContext request, int status, String code, String message) {
        boolean problemJson = ProblemDetail.requested(request.getHeaders().getFirst(HttpHeaders.ACCEPT));
        return Response.status(status)
                .type(ProblemDetail.contentType(problemJson))
                .entity(ProblemDetail.body(
                        status, code, message, problemJson, request.getUriInfo().getRequestUri()))
                .build();
    }

    /**
     * Creates the auth cookies; every one is SameSite=Strict, and the refresh token is sent only to {@code /api/auth}.
     *
     * @param settings decides the {@code Secure} attribute
     * @param headers the request headers
     * @param uriInfo the request URI
     */
    private record Cookies(SecuritySettings settings, HttpHeaders headers, UriInfo uriInfo) {

        NewCookie of(String name, String value, int maxAge, String path, boolean httpOnly) {
            return new NewCookie(
                    new Cookie(name, value, path, null),
                    null,
                    maxAge,
                    null,
                    settings.secureCookie(uriInfo.getRequestUri(), headers.getHeaderString(AuthFilter.FORWARDED_PROTO)),
                    httpOnly,
                    NewCookie.SameSite.STRICT);
        }
    }
}
