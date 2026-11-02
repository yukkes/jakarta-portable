package com.example.app.core.web;

import com.example.app.core.repository.HealthRepository;
import com.example.app.core.web.auth.OptionalAuth;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * The application health check ({@code GET /api/health}). Answers on the same path on every runtime.
 *
 * <p>The runtimes' MicroProfile Health paths differ (Quarkus {@code /q/health}, the EE servers {@code /health},
 * none on WildFly and GlassFish), so the conformance tests could not check them uniformly; hence this one.
 */
@Path("/health")
@ApplicationScoped
@Produces({MediaType.APPLICATION_JSON, "application/problem+json"})
public class HealthResource {

    @Inject
    HealthRepository healthRepository;

    /** Creates the resource. It is application-scoped; the repository is injected into a field. */
    public HealthResource() {}

    /**
     * Reports whether the application can reach its database. Needs no login.
     *
     * @return 200 with {@code UP} when a query succeeds, otherwise 503 with {@code DOWN}
     */
    @GET
    @OptionalAuth
    public Response health() {
        boolean database = healthRepository.isDatabaseReachable();
        Health result = new Health(database ? "UP" : "DOWN", database ? "UP" : "DOWN");
        return Response.status(database ? Response.Status.OK : Response.Status.SERVICE_UNAVAILABLE)
                .entity(result)
                .build();
    }

    /**
     * The health check's body.
     *
     * @param status the overall state, {@code UP} or {@code DOWN}
     * @param database the state of the database, {@code UP} or {@code DOWN}
     */
    public record Health(String status, String database) {}
}
