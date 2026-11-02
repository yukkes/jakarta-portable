package com.example.app.core.service;

import com.example.app.core.repository.InvoiceRepository;
import com.example.app.core.repository.OrderRepository;
import com.example.app.domain.Invoice;
import com.example.app.domain.Order;
import com.example.app.domain.OrderStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.YearMonth;
import java.util.List;

/**
 * DB access for the invoice batch. Transactions are opened with CDI {@code @Transactional}, so the batch
 * parts (reader, writer) do not depend on each runtime's batch transaction management.
 */
@ApplicationScoped
public class InvoiceBatchService {

    @Inject
    private OrderRepository orderRepository;

    @Inject
    private InvoiceRepository invoiceRepository;

    /** Creates the service. CDI creates the single instance (it is application-scoped) and injects its fields. */
    public InvoiceBatchService() {}

    /**
     * Returns one chunk of the month's orders, by ID, in its own transaction; the orders are detached when it returns.
     *
     * @param billingMonth {@code yyyy-MM}, already validated when the job was started
     * @param offset the index of the first order (the checkpoint)
     * @param limit the chunk size
     * @return the orders, oldest ID first
     */
    @Transactional
    public List<Order> findOrdersForMonth(String billingMonth, int offset, int limit) {
        return orderRepository.findForMonth(YearMonth.parse(billingMonth), offset, limit);
    }

    /**
     * Saves the invoice and marks the order INVOICED. Skips it when the invoice number already exists or the order is
     * no longer CONFIRMED, so a rerun never bills twice.
     *
     * @param invoices the invoices of one chunk
     */
    @Transactional
    public void saveInvoices(List<Invoice> invoices) {
        for (Invoice invoice : invoices) {
            if (invoiceRepository.existsByInvoiceNo(invoice.getInvoiceNo())) {
                continue;
            }

            Order managedOrder =
                    orderRepository.findById(invoice.getOrder().getId()).orElse(null);
            if (managedOrder == null || managedOrder.getStatus() != OrderStatus.CONFIRMED) {
                continue;
            }

            invoice.setOrder(managedOrder);
            invoice.setCustomer(managedOrder.getCustomer());
            invoiceRepository.persist(invoice);
            managedOrder.setStatus(OrderStatus.INVOICED);
        }
    }
}
