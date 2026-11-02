package com.example.app.core.web.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint callable without authentication. Every other endpoint needs {@link RequireRole} or
 * {@link OptionalAuth}; one with none of them is rejected with 403 by {@link RoleAuthorizationFilter}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface PublicEndpoint {}
