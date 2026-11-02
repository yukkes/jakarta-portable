package com.example.app.core.service;

import com.example.app.api.model.CreateOrderLine;
import com.example.app.api.model.CreateOrderRequest;
import com.example.app.api.model.OrderLineResponse;
import com.example.app.api.model.OrderResponse;
import com.example.app.core.event.OrderCancelledEvent;
import com.example.app.core.event.OrderConfirmedEvent;
import com.example.app.core.logging.Logged;
import com.example.app.core.repository.CustomerRepository;
import com.example.app.core.repository.OrderRepository;
import com.example.app.core.repository.ProductRepository;
import com.example.app.core.web.error.BusinessException;
import com.example.app.core.web.paging.PageQuery;
import com.example.app.core.web.paging.PageResponse;
import com.example.app.domain.BusinessTime;
import com.example.app.domain.Customer;
import com.example.app.domain.Order;
import com.example.app.domain.OrderLine;
import com.example.app.domain.OrderStatus;
import com.example.app.domain.Product;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Creating, confirming, cancelling and reading orders.
 *
 * <p>Confirming and cancelling move stock. Concurrent updates are detected by optimistic locking with
 * {@code @Version} on orders and products, and a conflict returns 409. Confirm and cancel fire CDI events
 * ({@code OrderAuditListener} receives them after commit).
 */
@Logged
@ApplicationScoped
public class OrderService {

    @Inject
    private OrderRepository orderRepository;

    @Inject
    private CustomerRepository customerRepository;

    @Inject
    private ProductRepository productRepository;

    @Inject
    private Event<OrderConfirmedEvent> confirmedEvents;

    @Inject
    private Event<OrderCancelledEvent> cancelledEvents;

    /** Creates the service. CDI creates the single instance (it is application-scoped) and injects its fields. */
    public OrderService() {}

    /**
     * Creates a DRAFT order. Without an order date, today is used.
     *
     * @param request the customer, the lines and optionally the order date
     * @return the created order
     * @throws BusinessException customer or product not found (404), quantity below 1 (400)
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Customer customer = findCustomer(request.getCustomerCode());
        Order order = new Order(
                newOrderNo(), customer, request.getOrderDate() != null ? request.getOrderDate() : BusinessTime.today());

        for (CreateOrderLine line : request.getLines()) {
            Product product = findProduct(line.getProductCode());
            // REST requests are rejected by Bean Validation first, but the CSV import goes through here.
            if (line.getQuantity() < 1) {
                throw BusinessException.badRequest("Quantity must be 1 or more: " + line.getProductCode());
            }
            order.addLine(product, line.getQuantity());
        }

        orderRepository.persist(order);
        orderRepository.flush();
        return toResponse(order);
    }

    /**
     * Confirms a DRAFT order and reserves its stock.
     *
     * @param id the order ID
     * @return the confirmed order
     * @throws BusinessException if it does not exist (404); if it is not DRAFT, stock is short, or on a concurrent
     *     update (409)
     */
    @Transactional
    public OrderResponse confirmOrder(Long id) {
        Order order = findOrder(id);
        if (order.getStatus() != OrderStatus.DRAFT) {
            throw BusinessException.conflict("Only DRAFT orders can be confirmed: " + order.getOrderNo());
        }
        for (OrderLine line : order.getLines()) {
            Product product = line.getProduct();
            if (product.getStock() < line.getQuantity()) {
                throw BusinessException.conflict("Insufficient stock: " + product.getCode() + " (stock="
                        + product.getStock() + ", requested=" + line.getQuantity() + ")");
            }
            // The product is managed, so dirty checking (and the @Version check) updates it.
            product.setStock(product.getStock() - line.getQuantity());
        }
        order.setStatus(OrderStatus.CONFIRMED);
        flushOrConflict();
        confirmedEvents.fire(new OrderConfirmedEvent(order.getId(), order.getOrderNo()));
        return toResponse(order);
    }

    /**
     * Cancels an order that has not been invoiced. Returns the stock if it was confirmed.
     *
     * @param id the order ID
     * @return the cancelled order
     * @throws BusinessException if it does not exist (404); if it is already invoiced or cancelled, or on a concurrent
     *     update (409)
     */
    @Transactional
    public OrderResponse cancelOrder(Long id) {
        Order order = findOrder(id);
        if (order.getStatus() == OrderStatus.INVOICED) {
            throw BusinessException.conflict("An invoiced order cannot be cancelled: " + order.getOrderNo());
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw BusinessException.conflict("Already cancelled: " + order.getOrderNo());
        }
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            for (OrderLine line : order.getLines()) {
                Product product = line.getProduct();
                product.setStock(product.getStock() + line.getQuantity());
            }
        }
        order.setStatus(OrderStatus.CANCELLED);
        flushOrConflict();
        cancelledEvents.fire(new OrderCancelledEvent(order.getId(), order.getOrderNo()));
        return toResponse(order);
    }

    /**
     * Returns one order with its lines.
     *
     * @param id the order ID
     * @return the order
     * @throws BusinessException when it does not exist (404)
     */
    @Transactional
    public OrderResponse getOrder(Long id) {
        return toResponse(findOrder(id));
    }

    /**
     * Returns one page of orders with their lines, and the total.
     *
     * @param status the status to filter by (case-insensitive); null or empty means every status
     * @param page the page, its size and the sort
     * @return the page
     * @throws BusinessException if the status is unknown or a sort field is not allowed (400)
     */
    @Transactional
    public PageResponse<OrderResponse> listOrders(String status, PageQuery page) {
        OrderStatus parsed = status == null || status.isBlank() ? null : parseStatus(status);
        var pageOfOrders = orderRepository.findPage(parsed, page.offset(), page.size(), page.sort());
        long total = orderRepository.count(parsed);
        return PageResponse.of(
                pageOfOrders.orders().stream()
                        .map(row -> toResponse(row, pageOfOrders.linesByOrder().getOrDefault(row.id(), List.of())))
                        .toList(),
                page,
                total);
    }

    private OrderStatus parseStatus(String status) {
        try {
            return OrderStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("Unknown status: " + status);
        }
    }

    private Customer findCustomer(String code) {
        return customerRepository
                .findByCode(code)
                .orElseThrow(() -> BusinessException.notFound("Customer not found: " + code));
    }

    private Product findProduct(String code) {
        return productRepository
                .findByCode(code)
                .orElseThrow(() -> BusinessException.notFound("Product not found: " + code));
    }

    private Order findOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Order not found: " + id));
    }

    /** Flushes here so that an optimistic-lock failure becomes a 409 instead of an exception at commit. */
    private void flushOrConflict() {
        try {
            orderRepository.flush();
        } catch (OptimisticLockException exception) {
            throw BusinessException.conflict("The order or the stock was updated concurrently. Please retry");
        }
    }

    private String newOrderNo() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private OrderResponse toResponse(Order order) {
        return orderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getCustomer().getCode(),
                order.getCustomer().getName(),
                order.getOrderDate(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getLines().stream()
                        .map(line -> lineResponse(
                                line.getProduct().getCode(),
                                line.getProduct().getName(),
                                line.getQuantity(),
                                line.getUnitPrice(),
                                line.getLineAmount()))
                        .toList());
    }

    /**
     * Builds the response of a list row, from projected rows only, so no entity is loaded.
     *
     * @param row the order row
     * @param lines its lines
     * @return the response
     */
    private OrderResponse toResponse(OrderRepository.OrderRow row, List<OrderRepository.LineRow> lines) {
        return orderResponse(
                row.id(),
                row.orderNo(),
                row.customerCode(),
                row.customerName(),
                row.orderDate(),
                row.status(),
                row.totalAmount(),
                lines.stream()
                        .map(line -> lineResponse(
                                line.productCode(),
                                line.productName(),
                                line.quantity(),
                                line.unitPrice(),
                                line.lineAmount()))
                        .toList());
    }

    private static OrderResponse orderResponse(
            Long id,
            String orderNo,
            String customerCode,
            String customerName,
            LocalDate orderDate,
            OrderStatus status,
            BigDecimal totalAmount,
            List<OrderLineResponse> lines) {
        OrderResponse response = new OrderResponse();
        response.setId(id);
        response.setOrderNo(orderNo);
        response.setCustomerCode(customerCode);
        response.setCustomerName(customerName);
        response.setOrderDate(orderDate);
        response.setStatus(OrderResponse.StatusEnum.valueOf(status.name()));
        response.setTotalAmount(totalAmount);
        response.setLines(lines);
        return response;
    }

    private static OrderLineResponse lineResponse(
            String productCode, String productName, int quantity, BigDecimal unitPrice, BigDecimal lineAmount) {
        OrderLineResponse response = new OrderLineResponse();
        response.setProductCode(productCode);
        response.setProductName(productName);
        response.setQuantity(quantity);
        response.setUnitPrice(unitPrice);
        response.setLineAmount(lineAmount);
        return response;
    }
}
