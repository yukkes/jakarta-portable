package com.example.app.core.repository;

import com.example.app.core.web.error.BusinessException;
import com.example.app.core.web.paging.SortOrder;
import com.example.app.domain.Product;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads and writes products. */
@ApplicationScoped
public class ProductRepository {

    private static final Map<String, String> SORTABLE = Map.of(
            "code", "p.code",
            "name", "p.name",
            "unitPrice", "p.unitPrice",
            "stock", "p.stock");

    @PersistenceContext
    EntityManager entityManager;

    /** Creates the repository. For CDI; the entity manager is injected into a field. */
    public ProductRepository() {}

    /**
     * Returns one page of products, projected into records (read-only).
     *
     * @param offset the index of the first row
     * @param limit the maximum number of rows
     * @param sort the requested sort; only fields in {@code SORTABLE}, applied before the unique fallback
     * @return the products of the page, by code unless {@code sort} says otherwise
     * @throws BusinessException if {@code sort} names a field that cannot be sorted (400)
     */
    public List<ProductRow> findPage(long offset, int limit, List<SortOrder> sort) {
        return entityManager
                .createQuery(
                        "select new " + ProductRow.class.getName() + "(p.code, p.name, p.unitPrice, p.stock)"
                                + " from Product p order by "
                                + Sorts.of(sort, SORTABLE, "p.code", SortOrder.Direction.ASC),
                        ProductRow.class)
                .setFirstResult(Math.toIntExact(offset))
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * Finds a product by its code, as a managed entity: stock changes are written back at commit.
     *
     * @param code the product code
     * @return the product, or empty if no product has this code
     */
    public Optional<Product> findByCode(String code) {
        return entityManager
                .createQuery("select p from Product p where p.code = :code", Product.class)
                .setParameter("code", code)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    /**
     * Counts the products, for the total of {@link #findPage}.
     *
     * @return the number of products
     */
    public long count() {
        return entityManager
                .createQuery("select count(p) from Product p", Long.class)
                .getSingleResult();
    }

    /**
     * Inserts a product at the next flush or commit; a duplicate code fails there.
     *
     * @param newProduct a product not yet persisted
     */
    public void persist(Product newProduct) {
        entityManager.persist(newProduct);
    }

    /** Writes pending inserts now. EclipseLink needs the products inserted before an order line refers to them. */
    public void flush() {
        entityManager.flush();
    }

    /**
     * One row of the product list.
     *
     * @param code the product code
     * @param name the product name
     * @param unitPrice the current unit price
     * @param stock the quantity in stock
     */
    public record ProductRow(String code, String name, BigDecimal unitPrice, int stock) {}
}
