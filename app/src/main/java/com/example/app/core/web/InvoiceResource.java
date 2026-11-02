package com.example.app.core.web;

import com.example.app.api.model.InvoiceResponse;
import com.example.app.core.service.InvoiceService;
import com.example.app.core.web.auth.RequireRole;
import com.example.app.core.web.error.BusinessException;
import com.example.app.core.web.paging.PageQuery;
import com.example.app.core.web.paging.PageResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** Invoice lists and lookups. */
@RequireRole({"USER", "ADMIN"})
@Path("/invoices")
@Produces({MediaType.APPLICATION_JSON, "application/problem+json"})
public class InvoiceResource {

    @Inject
    private InvoiceService invoiceService;

    /** Creates the resource. The runtime creates one per request and injects its fields. */
    public InvoiceResource() {}

    /**
     * Returns one page of invoices, newest first unless {@code sort} says otherwise.
     *
     * @param month the billing month, {@code yyyy-MM}; null or empty means every month
     * @param page the 0-based page number; negative values mean 0
     * @param size the page size, clamped to 1..100
     * @param sort {@code field,asc|desc} values, earlier ones first
     * @return the page and the totals
     * @throws BusinessException if a parameter is malformed or a sort field is not allowed (400)
     */
    @GET
    public PageResponse<InvoiceResponse> list(
            @QueryParam("month") String month,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("sort") List<String> sort) {
        return invoiceService.listInvoices(month, PageQuery.of(page, size, sort));
    }

    /**
     * Returns one invoice.
     *
     * @param id the invoice ID
     * @return the invoice
     * @throws BusinessException if it does not exist (404)
     */
    @GET
    @Path("/{id}")
    public InvoiceResponse get(@PathParam("id") Long id) {
        return invoiceService.getInvoice(id);
    }
}
