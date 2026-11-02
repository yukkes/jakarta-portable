-- A benchmark-sized catalogue: products, customers and orders with lines. Load it after the
-- runtime has started (a --dev build recreates the schema on every start), for example:
--   docker compose exec -T postgres psql -U orders -d orders \
--       -v products=200 -v customers=50 -v orders=200 < scripts/seed.sql
-- Orders matter as much as products: an empty orders table makes /api/orders look several times
-- faster than it is (an empty page is just JSON).
TRUNCATE order_lines, orders, invoices, products, customers RESTART IDENTITY CASCADE;

INSERT INTO customers (code, name, email)
SELECT 'C-' || lpad(g::text, 5, '0'), 'customer ' || g, 'c' || g || '@example.test'
FROM generate_series(1, :customers) g;

INSERT INTO products (code, name, unit_price, stock, version)
SELECT 'P-' || lpad(g::text, 5, '0'), 'item ' || g, (g * 100)::numeric(15,2), (g % 50) + 1, 0
FROM generate_series(1, :products) g;

INSERT INTO orders (order_no, customer_id, order_date, status, total_amount, version, created_at, updated_at)
SELECT 'ORD-B-' || lpad(g::text, 5, '0'),
       (SELECT id FROM customers ORDER BY id OFFSET (g % :customers) LIMIT 1),
       current_date - (g % 30), 'CONFIRMED', 0, 0, now(), now()
FROM generate_series(1, :orders) g;

INSERT INTO order_lines (order_id, product_id, quantity, unit_price, line_amount)
SELECT o.id, p.id, 2, p.unit_price, p.unit_price * 2
FROM orders o
JOIN LATERAL (SELECT id, unit_price FROM products ORDER BY id LIMIT 2) p ON true
WHERE o.order_no LIKE 'ORD-B-%';

UPDATE orders o
   SET total_amount = (SELECT coalesce(sum(l.line_amount), 0) FROM order_lines l WHERE l.order_id = o.id)
 WHERE o.order_no LIKE 'ORD-B-%';
