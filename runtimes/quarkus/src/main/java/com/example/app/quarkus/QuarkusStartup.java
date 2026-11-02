package com.example.app.quarkus;

import com.example.app.core.service.SeedService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Loads the data once Quarkus has started. Quarkus fires the application scope's initialization during static
 * init, before Hibernate is ready, so it replaces {@code StartupDataSeeder} of the app (excluded in
 * {@code application.properties}).
 */
@ApplicationScoped
public class QuarkusStartup {

    @Inject
    SeedService seedService;

    /** Creates the observer. For CDI. */
    public QuarkusStartup() {}

    void onStart(@Observes StartupEvent event) {
        seedService.seed();
    }
}
