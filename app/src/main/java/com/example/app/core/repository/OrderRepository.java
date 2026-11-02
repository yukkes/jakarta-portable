package com.example.app.core.repository;

import com.example.app.core.web.error.BusinessException;
import com.example.app.core.web.paging.SortOrder;
import com.example.app.domain.Order;
import com.example.app.domain.OrderStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads and writes orders. Updates rely on dirty checking and optimistic locking with {@code @Version}. */
@ApplicationScoped
public class OrderRepository {

    private static final Map<String, String> SORTABLE = Map.of(
            "id", "o.id",
            "orderNo", "o.orderNo",
            "orderDate", "o.orderDate",
            "status", "o.status",
            "totalAmount", "o.totalAmount",
            "createdAt", "o.createdAt",
            "updatedAt", "o.updatedAt");

    @PersistenceContext
    EntityManager entityManager;

    /** Creates the repository. For CDI; the entity manager is injected into a field. */
    public OrderRepository() {}

    /**
     * Finds an order by ID, as a managed entity for updates. Its lines and customer load lazily.
     *
     * @param id the order ID
     * @return the order, or empty if it does not exist
     */
    public Optional<Order> findById(Long id) {
        return entityManager
                .createQuery("select o from Order o where o.id = :id", Order.class)
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    /**
     * Returns one page of orders with their lines.
     *
     * <p>There is no collection fetch join: one query reads the orders (joined with the customer) and a second their
     * lines (by order ID), so paging stays SQL LIMIT/OFFSET and no Cartesian product needs deduplicating. The rows are
     * projected into records rather than entities, which avoids the persistence context's dirty checking (measurements
     * in docs/performance.md).
     *
     * @param status the status to filter by, or null for every status
     * @param offset the index of the first row
     * @param limit the maximum number of rows
     * @param sort the requested sort; only fields in {@code SORTABLE}, applied before the unique fallback
     * @return the orders of the page, newest first unless {@code sort} says otherwise, and their lines
     * @throws BusinessException if {@code sort} names a field that cannot be sorted (400)
     */
    public PageWithLines findPage(OrderStatus status, long offset, int limit, List<SortOrder> sort) {
        List<OrderRow> orders = forStatus(
                        "select new " + OrderRow.class.getName()
                                + "(o.id, o.orderNo, c.code, c.name, o.orderDate, o.status, o.totalAmount)"
                                + " from Order o join o.customer c",
                        " order by " + Sorts.of(sort, SORTABLE, "o.id", SortOrder.Direction.DESC),
                        status,
                        OrderRow.class)
                .setFirstResult(Math.toIntExact(offset))
                .setMaxResults(limit)
                .getResultList();
        if (orders.isEmpty()) {
            return new PageWithLines(List.of(), Map.of());
        }

        List<Long> ids = orders.stream().map(OrderRow::id).toList();
        List<LineRow> lines = entityManager
                .createQuery(
                        "select new " + LineRow.class.getName()
                                + "(l.order.id, p.code, p.name, l.quantity, l.unitPrice, l.lineAmount)"
                                + " from OrderLine l join l.product p where l.order.id in :ids order by l.id asc",
                        LineRow.class)
                .setParameter("ids", ids)
                .getResultList();

        Map<Long, List<LineRow>> byOrder = new LinkedHashMap<>();
        for (LineRow line : lines) {
            byOrder.computeIfAbsent(line.orderId(), key -> new ArrayList<>()).add(line);
        }
        return new PageWithLines(orders, byOrder);
    }

    /**
     * Counts the orders for the same condition as {@link #findPage}.
     *
     * @param status the status to filter by, or null for every status
     * @return the number of matching orders
     */
    public long count(OrderStatus status) {
        return forStatus("select count(o) from Order o", "", status, Long.class).getSingleResult();
    }

    /**
     * Returns, for the invoice batch, the orders whose order date falls in the month, by ID.
     *
     * <p>It does not filter by status, so the offset-based checkpoint does not shift when a chunk turns CONFIRMED
     * orders into INVOICED ones.
     *
     * @param month the billing month
     * @param offset the index of the first row (the checkpoint)
     * @param limit the chunk size
     * @return the orders as managed entities, oldest ID first
     */
    public List<Order> findForMonth(YearMonth month, int offset, int limit) {
        return entityManager
                .createQuery(
                        "select o from Order o where o.orderDate >= :from and o.orderDate < :to order by o.id asc",
                        Order.class)
                .setParameter("from", month.atDay(1))
                .setParameter("to", month.plusMonths(1).atDay(1))
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * Inserts an order and, by cascade, its lines at the next flush or commit.
     *
     * @param newOrder an order not yet persisted
     */
    public void persist(Order newOrder) {
        entityManager.persist(newOrder);
    }

    /**
     * Writes pending changes now, so that an optimistic-lock failure surfaces as {@code OptimisticLockException} at a
     * known point.
     */
    public void flush() {
        entityManager.flush();
    }

    /**
     * Builds the query with the status condition, if there is one, between the select and the order by.
     *
     * @param <T> the result type
     * @param select the select and from clauses
     * @param orderBy the order by clause, or an empty string
     * @param status the status to filter by, or null
     * @param type the result type
     * @return the query with its parameter bound
     */
    private <T> TypedQuery<T> forStatus(String select, String orderBy, OrderStatus status, Class<T> type) {
        if (status == null) {
            return entityManager.createQuery(select + orderBy, type);
        }
        return entityManager
                .createQuery(select + " where o.status = :status" + orderBy, type)
                .setParameter("status", status);
    }

    /**
     * One row of the order list: the order and its customer.
     *
     * @param id the order ID
     * @param orderNo the order number
     * @param customerCode the customer's code
     * @param customerName the customer's name
     * @param orderDate the order date
     * @param status the status
     * @param totalAmount the total of the lines
     */
    public record OrderRow(
            Long id,
            String orderNo,
            String customerCode,
            String customerName,
            LocalDate orderDate,
            OrderStatus status,
            BigDecimal totalAmount) {}

    /**
     * One line shown in the order list: the order line and its product.
     *
     * @param orderId the ID of the order it belongs to
     * @param productCode the product's code
     * @param productName the product's name
     * @param quantity the quantity
     * @param unitPrice the unit price when the line was added
     * @param lineAmount quantity times unit price
     */
    public record LineRow(
            Long orderId,
            String productCode,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineAmount) {}

    /**
     * The orders of a page, and their lines per order ID.
     *
     * @param orders the orders in page order
     * @param linesByOrder the lines per order ID; an order without lines has no entry
     */
    public record PageWithLines(List<OrderRow> orders, Map<Long, List<LineRow>> linesByOrder) {}
}
