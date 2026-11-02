package com.example.app.core.web.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The role a call requires. Checked by {@link RoleAuthorizationFilter}.
 *
 * <p>{@code @RolesAllowed} is not used: GlassFish and others then enable container-managed authentication and
 * reject the application's own login API before the JAX-RS filters run.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequireRole {
    /**
     * Returns the roles allowed; any one of them is enough. Without one the call gets 403 (401 if not logged in).
     *
     * @return the role names, such as {@code USER} or {@code ADMIN}
     */
    String[] value();
}
