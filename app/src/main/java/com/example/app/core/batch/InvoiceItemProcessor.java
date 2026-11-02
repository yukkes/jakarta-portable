package com.example.app.core.batch;

import com.example.app.domain.BusinessTime;
import com.example.app.domain.Invoice;
import com.example.app.domain.Order;
import com.example.app.domain.OrderStatus;
import jakarta.batch.api.BatchProperty;
import jakarta.batch.api.chunk.ItemProcessor;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * ItemProcessor that creates an invoice from a confirmed order. Orders that are not confirmed return null and are
 * filtered out; orders with an amount of 0 or less raise the skippable {@link InvalidOrderException}.
 */
@Dependent
@Named("invoiceItemProcessor")
public class InvoiceItemProcessor implements ItemProcessor {

    @Inject
    @BatchProperty(name = "billingMonth")
    private String billingMonth;

    /**
     * Creates the processor. The batch runtime creates it per step (it is a {@code @Named} batch artifact) and injects
     * its fields.
     */
    public InvoiceItemProcessor() {}

    @Override
    public Object processItem(Object item) {
        Order order = (Order) item;
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            return null;
        }
        if (order.getTotalAmount() == null || order.getTotalAmount().signum() <= 0) {
            throw new InvalidOrderException("Order cannot be invoiced: " + order.getOrderNo());
        }

        String month = billingMonth == null || billingMonth.isBlank()
                ? BusinessTime.currentMonth().minusMonths(1).toString()
                : billingMonth;
        BigDecimal total = order.getTotalAmount();
        BigDecimal tax = total.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        // The invoice number is derived from the order and the month, so a rerun yields the same number and no
        // duplicate.
        String invoiceNo = "INV-" + month.replace("-", "") + "-" + order.getId();

        return new Invoice(invoiceNo, order.getCustomer(), order, month, total, tax);
    }
}
