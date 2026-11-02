package com.example.app.core.event;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import java.util.logging.Logger;

/**
 * An example of a transactional observer. The audit log is written only when the transaction that confirmed or
 * cancelled the order commits (not when it rolls back).
 */
@ApplicationScoped
public class OrderAuditListener {

    private static final Logger LOG = Logger.getLogger(OrderAuditListener.class.getName());

    /** Creates the observer. CDI creates the single instance (it is application-scoped). */
    public OrderAuditListener() {}

    void onConfirmed(@Observes(during = TransactionPhase.AFTER_SUCCESS) OrderConfirmedEvent event) {
        LOG.info(() -> "[audit] order confirmed: id=" + event.orderId() + ", orderNo=" + event.orderNo());
    }

    void onCancelled(@Observes(during = TransactionPhase.AFTER_SUCCESS) OrderCancelledEvent event) {
        LOG.info(() -> "[audit] order cancelled: id=" + event.orderId() + ", orderNo=" + event.orderNo());
    }
}
