package com.example.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An order line. It copies the product's unit price at creation, so a later price change does not alter the amount.
 *
 * <p>PostgreSQL does not index foreign key columns automatically. The list looks up lines by order ID, hence the index.
 */
@Entity
@Table(name = "order_lines", indexes = @Index(name = "idx_order_lines_order", columnList = "order_id"))
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineAmount;

    /** Creates an empty instance. For JPA, which creates instances when it loads rows. */
    protected OrderLine() {}

    OrderLine(Order order, Product product, int quantity) {
        this.order = order;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = product.getUnitPrice();
        this.lineAmount =
                product.getUnitPrice().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Returns the product ordered (loaded lazily).
     *
     * @return the product
     */
    public Product getProduct() {
        return product;
    }

    /**
     * Returns the quantity.
     *
     * @return the quantity, at least 1
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * Returns the unit price when the line was added; later price changes do not affect it.
     *
     * @return the unit price
     */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /**
     * Returns quantity times unit price.
     *
     * @return the line amount, 2 decimals
     */
    public BigDecimal getLineAmount() {
        return lineAmount;
    }
}
