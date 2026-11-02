package com.example.app.core.web.auth;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Checks the roles of {@link RequireRole}.
 *
 * <p>Rejects the call unless the matched method allows it. It denies by default: an endpoint without a security
 * annotation is treated as a forgotten one, not a public API.
 */
@Provider
@Priority(Priorities.AUTHORIZATION + 100)
public class RoleAuthorizationFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(RoleAuthorizationFilter.class.getName());

    @Context
    private ResourceInfo resourceInfo;

    /** Creates the filter. The runtime discovers it through {@code @Provider} and injects its fields. */
    public RoleAuthorizationFilter() {}

    @Override
    public void filter(ContainerRequestContext request) {
        if (resourceInfo.getResourceMethod() == null) {
            return; // The runtime's automatic OPTIONS response. Anything else was already rejected by AuthFilter
        }
        if (Endpoints.has(resourceInfo, PublicEndpoint.class) || Endpoints.has(resourceInfo, OptionalAuth.class)) {
            return;
        }

        RequireRole roles = Endpoints.find(resourceInfo, RequireRole.class);
        if (roles == null) {
            LOG.warning(() -> "endpoint has no @RequireRole, @PublicEndpoint or @OptionalAuth: "
                    + resourceInfo.getResourceClass().getName() + "."
                    + resourceInfo.getResourceMethod().getName());
            request.abortWith(AuthResponses.forbidden(request, "Missing the required role"));
            return;
        }

        SecurityContext securityContext = request.getSecurityContext();
        if (securityContext == null || securityContext.getUserPrincipal() == null) {
            request.abortWith(AuthResponses.unauthorized(request, "Authentication required"));
            return;
        }
        boolean allowed = Arrays.stream(roles.value()).anyMatch(securityContext::isUserInRole);
        if (!allowed) {
            request.abortWith(AuthResponses.forbidden(request, "Missing the required role"));
        }
    }
}
