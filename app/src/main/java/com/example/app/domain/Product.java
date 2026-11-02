package com.example.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;

/** A product. Stock updates are protected by optimistic locking with {@code @Version}. */
@Entity
@Table(name = "products", uniqueConstraints = @UniqueConstraint(name = "uk_products_code", columnNames = "code"))
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int stock;

    @Version
    @Column(nullable = false)
    private long version;

    /** Creates an empty instance. For JPA, which creates instances when it loads rows. */
    protected Product() {}

    /**
     * Creates a product.
     *
     * @param code the unique product code
     * @param name the display name
     * @param unitPrice copied into each order line when the line is added; later changes do not affect existing orders
     * @param stock the initial quantity in stock
     */
    public Product(String code, String name, BigDecimal unitPrice, int stock) {
        this.code = code;
        this.name = name;
        this.unitPrice = unitPrice;
        this.stock = stock;
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
     * Returns the product code, the key the API and the CSV import use.
     *
     * @return the code
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the display name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the current unit price.
     *
     * @return the unit price
     */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /**
     * Returns the quantity in stock.
     *
     * @return the stock
     */
    public int getStock() {
        return stock;
    }

    /**
     * Sets the quantity in stock. Concurrent changes fail on {@code @Version} at flush.
     *
     * @param stock the new quantity, not negative
     */
    public void setStock(int stock) {
        this.stock = stock;
    }
}
