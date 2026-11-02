package com.example.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** A customer. Orders and invoices refer to it by customer code. */
@Entity
@Table(name = "customers", uniqueConstraints = @UniqueConstraint(name = "uk_customers_code", columnNames = "code"))
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String email;

    /** Creates an empty instance. For JPA, which creates instances when it loads rows. */
    protected Customer() {}

    /**
     * Creates a customer.
     *
     * @param code the unique business key used by the API and the CSV import
     * @param name the display name
     * @param email the contact address, or null
     */
    public Customer(String code, String name, String email) {
        this.code = code;
        this.name = name;
        this.email = email;
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
     * Returns the customer code, the key the API and the CSV import use.
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
     * Returns the contact address.
     *
     * @return the address, or null
     */
    public String getEmail() {
        return email;
    }
}
