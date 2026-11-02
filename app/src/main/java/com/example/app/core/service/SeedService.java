package com.example.app.core.service;

import com.example.app.core.repository.CustomerRepository;
import com.example.app.core.repository.OrderRepository;
import com.example.app.core.repository.ProductRepository;
import com.example.app.core.repository.UserRepository;
import com.example.app.core.security.Passwords;
import com.example.app.core.security.SecuritySettings;
import com.example.app.domain.BusinessTime;
import com.example.app.domain.Customer;
import com.example.app.domain.Order;
import com.example.app.domain.OrderStatus;
import com.example.app.domain.Product;
import com.example.app.domain.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Data loaded at startup. Users are created only when configured ({@code SecuritySettings}); the demo
 * customers, products and orders only when there is no product at all.
 */
@ApplicationScoped
public class SeedService {

    private static final Logger LOG = Logger.getLogger(SeedService.class.getName());

    @Inject
    private ProductRepository productRepository;

    @Inject
    private CustomerRepository customerRepository;

    @Inject
    private OrderRepository orderRepository;

    @Inject
    private UserRepository userRepository;

    @Inject
    private Passwords passwords;

    @Inject
    private SecuritySettings settings;

    /** Creates the service. CDI creates the single instance (it is application-scoped) and injects its fields. */
    public SeedService() {}

    /**
     * Creates the configured users if there are none, then the demo data if there is no product. Safe to run on
     * every start: it never changes or adds to existing data.
     */
    @Transactional
    public void seed() {
        seedUsers();

        if (productRepository.count() > 0) {
            return;
        }

        Customer yamada = new Customer("C001", "Yamada Trading Co., Ltd.", "sales@yamada.example");
        Customer sato = new Customer("C002", "Sato Industries Inc.", "buy@sato.example");
        Customer suzuki = new Customer("C003", "Suzuki Store", "info@suzuki.example");
        customerRepository.persist(yamada);
        customerRepository.persist(sato);
        customerRepository.persist(suzuki);

        Product laptop = new Product("P001", "Laptop", new BigDecimal("120000.00"), 20);
        Product mouse = new Product("P002", "Wireless Mouse", new BigDecimal("3000.00"), 100);
        Product monitor = new Product("P003", "27-inch Monitor", new BigDecimal("45000.00"), 10);
        Product cable = new Product("P004", "USB-C Cable", new BigDecimal("800.00"), 500);
        productRepository.persist(laptop);
        productRepository.persist(mouse);
        productRepository.persist(monitor);
        productRepository.persist(cable);
        // EclipseLink (Liberty, Payara) rejects an order that refers to a product not yet INSERTed
        // ("not marked cascade PERSIST"). Flush the products before creating the orders.
        productRepository.flush();

        LocalDate lastMonth = BusinessTime.currentMonth().minusMonths(1).atDay(10);

        Order order1 = new Order("ORD-SEED-001", yamada, lastMonth);
        order1.addLine(laptop, 1);
        order1.addLine(mouse, 2);
        order1.setStatus(OrderStatus.CONFIRMED);
        orderRepository.persist(order1);

        Order order2 = new Order("ORD-SEED-002", sato, lastMonth.plusDays(2));
        order2.addLine(mouse, 5);
        order2.setStatus(OrderStatus.CONFIRMED);
        orderRepository.persist(order2);

        Order order3 = new Order("ORD-SEED-003", suzuki, lastMonth.plusDays(5));
        order3.addLine(monitor, 2);
        order3.setStatus(OrderStatus.CONFIRMED);
        orderRepository.persist(order3);

        Order draft = new Order("ORD-SEED-004", yamada, BusinessTime.today());
        draft.addLine(cable, 3);
        orderRepository.persist(draft);
    }

    private void seedUsers() {
        if (userRepository.count() > 0) {
            return;
        }

        boolean admin = seedUser("admin", "ADMIN");
        boolean user = seedUser("user", "USER");
        if (!admin && !user) {
            LOG.warning("No users were created, so nobody can log in. Set app.admin-password "
                    + "(APP_ADMIN_PASSWORD) or, for local demos only, app.demo-users=true.");
        }
    }

    private boolean seedUser(String username, String role) {
        Optional<String> password = settings.initialPassword(username);
        if (password.isEmpty()) {
            return false;
        }
        userRepository.persist(new User(username, passwords.hash(password.get()), role));
        return true;
    }
}
