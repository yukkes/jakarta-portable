package com.example.app.core.web;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/** Adds the headers of {@link SecurityHeaders} to every JAX-RS response. */
@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {

    /** Creates the filter. The runtime discovers it through {@code @Provider}. */
    public SecurityHeadersFilter() {}

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        SecurityHeaders.responseHeaders().forEach(response.getHeaders()::putSingle);
    }
}
