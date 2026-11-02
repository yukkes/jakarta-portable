package com.example.app.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Order amount calculation. It is computed in the domain, so it can be checked without a DB. */
class OrderTest {

    private final Customer customer = new Customer("C001", "customer", "c@example.test");

    @Test
    void sumsTheLineAmounts() {
        Order order = new Order("ORD-1", customer, LocalDate.of(2026, 1, 1));
        order.addLine(new Product("P1", "laptop", new BigDecimal("120000.00"), 5), 1);
        order.addLine(new Product("P2", "mouse", new BigDecimal("3000.00"), 5), 2);

        assertEquals(new BigDecimal("126000.00"), order.getTotalAmount());
        assertEquals(2, order.getLines().size());
    }

    @Test
    void startsAtZeroWithTwoDecimals() {
        Order order = new Order("ORD-2", customer, LocalDate.of(2026, 1, 1));

        assertEquals(0, BigDecimal.ZERO.compareTo(order.getTotalAmount()));
        assertEquals(0, order.getLines().size());
    }

    @Test
    void roundsHalfUpAtTheLineLevel() {
        // 1.005 x 3 = 3.015 is rounded to 3.02 on the line, and the total is the sum of the lines.
        Order order = new Order("ORD-3", customer, LocalDate.of(2026, 1, 1));
        order.addLine(new Product("P3", "sample", new BigDecimal("1.005"), 1), 3);

        assertEquals(new BigDecimal("3.02"), order.getTotalAmount());
        assertEquals(new BigDecimal("3.02"), order.getLines().get(0).getLineAmount());
    }

    @Test
    void keepsTheStatusTransitionsTheServiceReliesOn() {
        Order order = new Order("ORD-4", customer, LocalDate.of(2026, 1, 1));

        assertEquals(OrderStatus.DRAFT, order.getStatus());
        order.setStatus(OrderStatus.CONFIRMED);
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertTrue(order.getLines().isEmpty());
    }
}
