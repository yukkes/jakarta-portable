package com.example.app.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An order. It aggregates its lines ({@link OrderLine}), and the total is computed from them.
 *
 * <p>Optimistic locking with {@code @Version} detects concurrent updates of one order (such as confirm and cancel).
 */
@Entity
@Table(
        name = "orders",
        indexes = {
            @Index(name = "idx_orders_date", columnList = "order_date"),
            @Index(name = "idx_orders_status", columnList = "status")
        })
public class Order extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 30, unique = true)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.DRAFT;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> lines = new ArrayList<>();

    /** Creates an empty instance. For JPA, which creates instances when it loads rows. */
    protected Order() {}

    /**
     * Creates a DRAFT order with no lines and a total of 0; add lines with {@link #addLine}.
     *
     * @param orderNo the unique order number
     * @param customer the customer who ordered
     * @param orderDate the order date
     */
    public Order(String orderNo, Customer customer, LocalDate orderDate) {
        this.orderNo = orderNo;
        this.customer = customer;
        this.orderDate = orderDate;
    }

    /**
     * Adds a line at the product's current unit price and recomputes the total.
     *
     * @param product the product ordered
     * @param quantity the quantity, at least 1
     */
    public void addLine(Product product, int quantity) {
        lines.add(new OrderLine(this, product, quantity));
        totalAmount = lines.stream()
                .map(OrderLine::getLineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Returns the ID, assigned by the database.
     *
     * @return the ID, or null until the entity is first flushed
     */
    public Long getId() {
        return id;
    }

    /**
     * Returns the order number.
     *
     * @return the unique order number
     */
    public String getOrderNo() {
        return orderNo;
    }

    /**
     * Returns the customer who ordered (loaded lazily).
     *
     * @return the customer
     */
    public Customer getCustomer() {
        return customer;
    }

    /**
     * Returns the order date.
     *
     * @return the date
     */
    public LocalDate getOrderDate() {
        return orderDate;
    }

    /**
     * Returns the status.
     *
     * @return the status
     */
    public OrderStatus getStatus() {
        return status;
    }

    /**
     * Sets the status. The services check that the transition is allowed; this does not.
     *
     * @param status the new status
     */
    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    /**
     * Returns the total of the lines.
     *
     * @return the total, 2 decimals
     */
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /**
     * Returns the lines (loaded lazily).
     *
     * @return an unmodifiable view of the lines; add lines with {@link #addLine}
     */
    public List<OrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
