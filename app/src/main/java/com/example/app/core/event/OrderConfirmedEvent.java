package com.example.app.core.event;

/**
 * An order was confirmed.
 *
 * @param orderId the order ID
 * @param orderNo the order number
 */
public record OrderConfirmedEvent(Long orderId, String orderNo) {}
