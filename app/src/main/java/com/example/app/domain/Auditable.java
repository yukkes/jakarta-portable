package com.example.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;

/** Creation and update timestamp columns. Set by JPA callbacks, so they work on both Hibernate and EclipseLink. */
@MappedSuperclass
public abstract class Auditable {

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Creates the timestamps' holder; the timestamps are set when the entity is persisted. */
    protected Auditable() {}

    @PrePersist
    void onCreate() {
        LocalDateTime now = BusinessTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = BusinessTime.now();
    }

    /**
     * Returns when the row was inserted, in {@code BusinessTime.zone()}.
     *
     * @return the creation time, or null until the entity is persisted
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
