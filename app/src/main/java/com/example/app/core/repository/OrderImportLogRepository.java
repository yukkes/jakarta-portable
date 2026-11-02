package com.example.app.core.repository;

import com.example.app.domain.OrderImportLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Reads and writes the CSV import records ({@link OrderImportLog}). */
@ApplicationScoped
public class OrderImportLogRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /** Creates the repository. For CDI; the entity manager is injected into a field. */
    public OrderImportLogRepository() {}

    /**
     * Tells whether an earlier (or the running) import already imported this line.
     *
     * @param importKey the line's key ({@code ImportCsvLine.importKey})
     * @return true if the line was imported
     */
    public boolean isImported(String importKey) {
        return entityManager.find(OrderImportLog.class, importKey) != null;
    }

    /**
     * Records that this line was imported in the current transaction.
     *
     * <p>If imports of the same file run concurrently, one of them fails on the primary key's unique constraint.
     *
     * @param importKey the line's key ({@code ImportCsvLine.importKey})
     */
    public void markImported(String importKey) {
        entityManager.persist(new OrderImportLog(importKey));
    }
}
