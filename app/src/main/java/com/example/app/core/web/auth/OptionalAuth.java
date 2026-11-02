package com.example.app.core.web.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint that can be called anonymously but verifies a token if one is present (for example
 * {@code /api/auth/status}). An invalid token gets 401.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface OptionalAuth {}
