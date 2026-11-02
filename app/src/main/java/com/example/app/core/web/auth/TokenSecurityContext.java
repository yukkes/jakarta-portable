package com.example.app.core.web.auth;

import com.example.app.core.service.AuthService.AuthenticatedUser;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;

/** A {@code SecurityContext} for the user and roles of a verified access token. */
public final class TokenSecurityContext implements SecurityContext {

    private final AuthenticatedUser user;
    private final boolean secure;
    private final String authenticationScheme;

    /**
     * Creates the context of a verified access token.
     *
     * @param user the user and roles from the token
     * @param secure whether the request counts as HTTPS ({@code SecuritySettings.isSecureRequest})
     * @param authenticationScheme {@code Bearer} or {@code Cookie}, whichever carried the token
     */
    public TokenSecurityContext(AuthenticatedUser user, boolean secure, String authenticationScheme) {
        this.user = user;
        this.secure = secure;
        this.authenticationScheme = authenticationScheme;
    }

    @Override
    public Principal getUserPrincipal() {
        return () -> user.username();
    }

    @Override
    public boolean isUserInRole(String role) {
        return user.roles().contains(role);
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public String getAuthenticationScheme() {
        return authenticationScheme;
    }
}
