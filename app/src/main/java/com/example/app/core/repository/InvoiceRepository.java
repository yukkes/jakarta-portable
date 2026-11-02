package com.example.app.core.repository;

import com.example.app.core.web.error.BusinessException;
import com.example.app.core.web.paging.SortOrder;
import com.example.app.domain.Invoice;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads and writes invoices. */
@ApplicationScoped
public class InvoiceRepository {

    private static final Map<String, String> SORTABLE = Map.of(
            "id", "i.id",
            "invoiceNo", "i.invoiceNo",
            "billingMonth", "i.billingMonth",
            "totalAmount", "i.totalAmount");

    @PersistenceContext
    EntityManager entityManager;

    /** Creates the repository. For CDI; the entity manager is injected into a field. */
    public InvoiceRepository() {}

    /**
     * Finds an invoice by ID.
     *
     * @param id the invoice ID
     * @return the invoice, or empty if it does not exist
     */
    public Optional<Invoice> findById(Long id) {
        return entityManager
                .createQuery("select i from Invoice i where i.id = :id", Invoice.class)
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    /**
     * Returns one page of invoices.
     *
     * @param billingMonth {@code yyyy-MM}, or null for every month
     * @param offset the index of the first row
     * @param limit the maximum number of rows
     * @param sort the requested sort; only fields in {@code SORTABLE}, applied before the unique fallback
     * @return the invoices of the page, newest first unless {@code sort} says otherwise
     * @throws BusinessException if {@code sort} names a field that cannot be sorted (400)
     */
    public List<Invoice> findPage(String billingMonth, long offset, int limit, List<SortOrder> sort) {
        return forMonth(
                        "select i from Invoice i",
                        " order by " + Sorts.of(sort, SORTABLE, "i.id", SortOrder.Direction.DESC),
                        billingMonth,
                        Invoice.class)
                .setFirstResult(Math.toIntExact(offset))
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * Counts the invoices for the same condition as {@link #findPage}.
     *
     * @param billingMonth {@code yyyy-MM}, or null for every month
     * @return the number of matching invoices
     */
    public long count(String billingMonth) {
        return forMonth("select count(i) from Invoice i", "", billingMonth, Long.class)
                .getSingleResult();
    }

    /**
     * Tells whether an invoice with this number exists, without loading it.
     *
     * <p>The invoice number is derived from the month and the order, so the batch uses this to skip orders it already
     * billed.
     *
     * @param invoiceNo the invoice number
     * @return true if an invoice has this number
     */
    public boolean existsByInvoiceNo(String invoiceNo) {
        return !entityManager
                .createQuery("select i.id from Invoice i where i.invoiceNo = :invoiceNo", Long.class)
                .setParameter("invoiceNo", invoiceNo)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    /**
     * Inserts an invoice at the next flush or commit; a duplicate invoice number fails there.
     *
     * @param newInvoice an invoice not yet persisted
     */
    public void persist(Invoice newInvoice) {
        entityManager.persist(newInvoice);
    }

    /**
     * Builds the query with the month condition, if there is one, between the select and the order by.
     *
     * @param <T> the result type
     * @param select the select and from clauses
     * @param orderBy the order by clause, or an empty string
     * @param billingMonth {@code yyyy-MM}, or null for every month
     * @param type the result type
     * @return the query with its parameter bound
     */
    private <T> TypedQuery<T> forMonth(String select, String orderBy, String billingMonth, Class<T> type) {
        if (billingMonth == null) {
            return entityManager.createQuery(select + orderBy, type);
        }
        return entityManager
                .createQuery(select + " where i.billingMonth = :billingMonth" + orderBy, type)
                .setParameter("billingMonth", billingMonth);
    }
}
