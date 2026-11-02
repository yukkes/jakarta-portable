package com.example.app.domain;

/** Order status. Moves DRAFT -> CONFIRMED -> INVOICED, and can be cancelled (CANCELLED) before invoicing. */
public enum OrderStatus {
    /** Created; stock is not reserved yet. */
    DRAFT,
    /** Confirmed; its quantities are taken out of stock. */
    CONFIRMED,
    /** Billed by the invoice job; it can no longer be cancelled. */
    INVOICED,
    /** Cancelled; stock taken by a confirmation was returned. */
    CANCELLED
}
