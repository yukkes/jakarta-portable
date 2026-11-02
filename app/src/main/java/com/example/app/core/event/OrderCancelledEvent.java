package com.example.app.core.event;

/**
 * An order was cancelled.
 *
 * @param orderId the order ID
 * @param orderNo the order number
 */
public record OrderCancelledEvent(Long orderId, String orderNo) {}
