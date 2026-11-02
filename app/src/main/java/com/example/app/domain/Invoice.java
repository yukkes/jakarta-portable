package com.example.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;

/**
 * An invoice. Its number derives from the order and the billing month ({@code INV-yyyyMM-orderId}), so an order is
 * never billed twice.
 */
@Entity
@Table(name = "invoices", uniqueConstraints = @UniqueConstraint(name = "uk_invoices_no", columnNames = "invoice_no"))
public class Invoice extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_no", nullable = false, length = 40)
    private String invoiceNo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "billing_month", nullable = false, length = 7)
    private String billingMonth;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxAmount;

    /** Creates an empty instance. For JPA, which creates instances when it loads rows. */
    protected Invoice() {}

    /**
     * Creates an invoice for one order.
     *
     * @param invoiceNo unique; {@code INV-yyyyMM-<order ID>}, so a rerun of the batch finds it
     * @param customer the customer billed
     * @param order the order billed
     * @param billingMonth {@code yyyy-MM}
     * @param totalAmount the order total
     * @param taxAmount already rounded to 2 decimals
     */
    public Invoice(
            String invoiceNo,
            Customer customer,
            Order order,
            String billingMonth,
            BigDecimal totalAmount,
            BigDecimal taxAmount) {
        this.invoiceNo = invoiceNo;
        this.customer = customer;
        this.order = order;
        this.billingMonth = billingMonth;
        this.totalAmount = totalAmount;
        this.taxAmount = taxAmount;
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
     * Returns the invoice number, {@code INV-yyyyMM-<order ID>}.
     *
     * @return the number
     */
    public String getInvoiceNo() {
        return invoiceNo;
    }

    /**
     * Returns the customer billed (loaded with the invoice).
     *
     * @return the customer
     */
    public Customer getCustomer() {
        return customer;
    }

    /**
     * Sets the customer billed; the batch replaces it with the managed instance before saving.
     *
     * @param customer the customer
     */
    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    /**
     * Returns the order billed (loaded with the invoice).
     *
     * @return the order
     */
    public Order getOrder() {
        return order;
    }

    /**
     * Sets the order billed; the batch replaces it with the managed instance before saving.
     *
     * @param order the order
     */
    public void setOrder(Order order) {
        this.order = order;
    }

    /**
     * Returns the billing month.
     *
     * @return {@code yyyy-MM}
     */
    public String getBillingMonth() {
        return billingMonth;
    }

    /**
     * Returns the amount billed, before tax.
     *
     * @return the amount, 2 decimals
     */
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /**
     * Returns the tax on the amount billed.
     *
     * @return the tax, 2 decimals
     */
    public BigDecimal getTaxAmount() {
        return taxAmount;
    }
}
