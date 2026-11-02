package com.example.app.core.batch;

import com.example.app.core.service.InvoiceBatchService;
import com.example.app.domain.Invoice;
import jakarta.batch.api.chunk.AbstractItemWriter;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;

/** ItemWriter that saves invoices in bulk. */
@Dependent
@Named("invoiceItemWriter")
public class InvoiceItemWriter extends AbstractItemWriter {

    @Inject
    private InvoiceBatchService invoiceBatchService;

    /**
     * Creates the writer. The batch runtime creates it per step (it is a {@code @Named} batch artifact) and injects its
     * fields.
     */
    public InvoiceItemWriter() {}

    @Override
    public void writeItems(List<Object> items) {
        List<Invoice> invoices = items.stream().map(Invoice.class::cast).toList();
        invoiceBatchService.saveInvoices(invoices);
    }
}
