package com.example.app.core.service;

import com.example.app.api.model.InvoiceResponse;
import com.example.app.core.repository.InvoiceRepository;
import com.example.app.core.web.error.BusinessException;
import com.example.app.core.web.paging.PageQuery;
import com.example.app.core.web.paging.PageResponse;
import com.example.app.domain.Invoice;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** Invoice lists and lookups. */
@ApplicationScoped
public class InvoiceService {

    @Inject
    private InvoiceRepository invoiceRepository;

    /** Creates the service. CDI creates the single instance (it is application-scoped) and injects its fields. */
    public InvoiceService() {}

    /**
     * Returns one page of invoices and the total.
     *
     * @param month billing month ({@code yyyy-MM}); null or empty means every month
     * @param page the page, its size and the sort
     * @return the page
     * @throws BusinessException if the month is malformed or a sort field is not allowed (400)
     */
    @Transactional
    public PageResponse<InvoiceResponse> listInvoices(String month, PageQuery page) {
        String billingMonth = month == null || month.isBlank() ? null : normalizeMonth(month);
        var invoices = invoiceRepository.findPage(billingMonth, page.offset(), page.size(), page.sort());
        long total = invoiceRepository.count(billingMonth);
        return PageResponse.of(invoices.stream().map(this::toResponse).toList(), page, total);
    }

    /**
     * Returns one invoice.
     *
     * @param id the invoice ID
     * @return the invoice
     * @throws BusinessException when it does not exist (404)
     */
    @Transactional
    public InvoiceResponse getInvoice(Long id) {
        Invoice invoice = invoiceRepository
                .findById(id)
                .orElseThrow(() -> BusinessException.notFound("Invoice not found: " + id));
        return toResponse(invoice);
    }

    private String normalizeMonth(String month) {
        try {
            return YearMonth.parse(month).toString();
        } catch (DateTimeParseException exception) {
            throw BusinessException.badRequest("Specify the month as YYYY-MM: " + month);
        }
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        InvoiceResponse response = new InvoiceResponse();
        response.setId(invoice.getId());
        response.setInvoiceNo(invoice.getInvoiceNo());
        response.setCustomerCode(invoice.getCustomer().getCode());
        response.setCustomerName(invoice.getCustomer().getName());
        response.setOrderNo(invoice.getOrder().getOrderNo());
        response.setBillingMonth(invoice.getBillingMonth());
        response.setTotalAmount(invoice.getTotalAmount());
        response.setTaxAmount(invoice.getTaxAmount());
        response.setCreatedAt(invoice.getCreatedAt());
        return response;
    }
}
