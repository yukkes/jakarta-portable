package com.example.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The record of one imported CSV line. The primary key is the line's deduplication key, so a line yields one order.
 *
 * <p>It is recorded in the same transaction as the order, so a line whose import fails is rolled back together with
 * its record and can be imported again by a rerun.
 */
@Entity
@Table(name = "order_import_log")
public class OrderImportLog extends Auditable {

    @Id
    @Column(name = "import_key", nullable = false, length = 64, updatable = false)
    private String importKey;

    /** Creates an empty instance. For JPA, which creates instances when it loads rows. */
    protected OrderImportLog() {}

    /**
     * Creates the record of one imported line.
     *
     * @param importKey identifies one CSV line ({@code ImportCsvLine.importKey}: a hash of the file name, the line and
     *     its occurrence)
     */
    public OrderImportLog(String importKey) {
        this.importKey = importKey;
    }
}
