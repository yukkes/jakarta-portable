package com.example.app.core.repository;

import com.example.app.core.web.error.BusinessException;
import com.example.app.core.web.paging.SortOrder;
import com.example.app.domain.Customer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads and writes customers. */
@ApplicationScoped
public class CustomerRepository {

    private static final Map<String, String> SORTABLE = Map.of("code", "c.code", "name", "c.name");

    @PersistenceContext
    EntityManager entityManager;

    /** Creates the repository. For CDI; the entity manager is injected into a field. */
    public CustomerRepository() {}

    /**
     * Returns one page of customers.
     *
     * @param offset the index of the first row
     * @param limit the maximum number of rows
     * @param sort the requested sort; only fields in {@code SORTABLE}, applied before the unique fallback
     * @return the customers of the page, by code unless {@code sort} says otherwise
     * @throws BusinessException if {@code sort} names a field that cannot be sorted (400)
     */
    public List<Customer> findPage(long offset, int limit, List<SortOrder> sort) {
        return entityManager
                .createQuery(
                        "select c from Customer c order by "
                                + Sorts.of(sort, SORTABLE, "c.code", SortOrder.Direction.ASC),
                        Customer.class)
                .setFirstResult(Math.toIntExact(offset))
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * Counts the customers, for the total of {@link #findPage}.
     *
     * @return the number of customers
     */
    public long count() {
        return entityManager
                .createQuery("select count(c) from Customer c", Long.class)
                .getSingleResult();
    }

    /**
     * Finds a customer by its code.
     *
     * @param code the customer code
     * @return the customer as a managed entity, or empty if no customer has this code
     */
    public Optional<Customer> findByCode(String code) {
        return entityManager
                .createQuery("select c from Customer c where c.code = :code", Customer.class)
                .setParameter("code", code)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    /**
     * Inserts a customer at the next flush or commit; a duplicate code fails there.
     *
     * @param newCustomer a customer not yet persisted
     */
    public void persist(Customer newCustomer) {
        entityManager.persist(newCustomer);
    }
}
