package com.example.app.core.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * DB connectivity check for {@code GET /api/health}. Uses a one-row JPQL query so it does not depend on the runtime.
 */
@ApplicationScoped
public class HealthRepository {

    @PersistenceContext
    EntityManager entityManager;

    /** Creates the repository. For CDI; the entity manager is injected into a field. */
    public HealthRepository() {}

    /**
     * Tells whether a query against the database succeeds.
     *
     * @return false instead of an exception when the query fails for any reason (no connection, pool exhausted,
     *     timeout)
     */
    public boolean isDatabaseReachable() {
        try {
            entityManager
                    .createQuery("select count(c) from Customer c", Long.class)
                    .getSingleResult();
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
