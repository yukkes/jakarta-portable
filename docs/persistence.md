# Persistence

All data access is Jakarta Persistence: `EntityManager` for writes, JPQL for reads, in `core/repository/`. No query library.

## Rules

- **Writes**: `persist`, dirty checking of managed entities, `@Version` for optimistic locking. A conflict is flushed early (`OrderService`) so it becomes a 409 rather than a failed commit.
- **Read-only lists**: projected into records with a constructor expression (`select new ...Row(...)`). Nothing enters the persistence context, so there is no dirty checking at flush; on the order list that saved about 20% of the CPU.
- **One-to-many in a list**: two queries, never a collection fetch join. The order list reads one page of orders (joined with the customer, SQL `LIMIT`/`OFFSET`), then the lines of those orders with `where l.order.id in :ids`. A fetch join of the lines would page in memory and multiply the rows.
- **Sorting**: each repository's `SORTABLE` maps an API field to a constant JPQL path; `Sorts` builds the `order by` from it and appends a unique column, so pages stay stable when the sorted column has duplicates.
- **Page and count**: one helper adds the `where` to both queries (`forStatus`, `forMonth`), so they cannot disagree.
- **Counts**: `getSingleResult()` without `setMaxResults` (a limit adds a needless `LIMIT 1`). Single-row lookups: `setMaxResults(1)` and `getResultStream().findFirst()`.
- **Portability**: `join fetch` without an alias (WildFly runs Hibernate in strict JPQL mode); no `<jta-data-source>` (every runtime's default data source is used); provider settings for both Hibernate and EclipseLink sit side by side in `persistence.xml`.

## The SQL that runs

`log_statement=all` on Quarkus (Hibernate):

| Request | Statements |
|---|---|
| `GET /api/products?sort=unitPrice,desc` | the page (`order by unit_price desc, code`, `offset ... fetch first ... rows only`) and `count` |
| `GET /api/orders` | the orders joined with customers, their lines joined with products (`order_id in (...)`), and `count` |
| `GET /api/invoices` | the page and `count` |
| `GET /api/orders/{id}` | the order, its customer, its lines, their products (lazy loads) |

Paging happens in SQL; Hibernate's in-memory paging warning (`HHH000104`) never appears.

## Why not Jakarta Data

Jakarta Data 1.0 ran on five of the runtimes but was removed:

- Hibernate's implementation (Quarkus, WildFly) saves through `StatelessSession`, which skips `@PrePersist`, so writes needed `EntityManager` anyway.
- 1.0 has no `count()` or `existsById()`, and its pages are 1-based.
- Derived queries such as `findByCode` were not generated on every provider; `@Find`/`@By` were needed.
- It needed per-runtime setup: a separate WildFly WAR, Liberty's `data-1.0` feature, a Galleon layer.

Removing it took out about 115 lines of code and one WAR variant. It is worth another look once it can be limited to reads without those workarounds.
