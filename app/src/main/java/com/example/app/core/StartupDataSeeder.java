package com.example.app.core;

import com.example.app.core.service.SeedService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Loads data at startup. It observes the initialization of the application scope (CDI 1.1+), which every
 * runtime fires, including Quarkus; the CDI 4.1 {@code Startup} event is not part of Jakarta EE 10.
 */
@ApplicationScoped
public class StartupDataSeeder {

    @Inject
    private SeedService seedService;

    /** Creates the observer. CDI creates it for the startup event and injects its fields. */
    public StartupDataSeeder() {}

    void onStart(@Observes @Initialized(ApplicationScoped.class) Object event) {
        seedService.seed();
    }
}
