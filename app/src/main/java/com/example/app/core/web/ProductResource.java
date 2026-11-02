package com.example.app.core.web;

import com.example.app.api.model.ProductResponse;
import com.example.app.core.service.CatalogService;
import com.example.app.core.web.auth.RequireRole;
import com.example.app.core.web.error.BusinessException;
import com.example.app.core.web.paging.PageQuery;
import com.example.app.core.web.paging.PageResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** The product list. */
@RequireRole({"USER", "ADMIN"})
@Path("/products")
@Produces({MediaType.APPLICATION_JSON, "application/problem+json"})
public class ProductResource {

    @Inject
    private CatalogService catalogService;

    /** Creates the resource. The runtime creates one per request and injects its fields. */
    public ProductResource() {}

    /**
     * Returns one page of products with their current stock, by code unless {@code sort} says otherwise.
     *
     * @param page the 0-based page number; negative values mean 0
     * @param size the page size, clamped to 1..100
     * @param sort {@code field,asc|desc} values, earlier ones first
     * @return the page and the totals
     * @throws BusinessException if a parameter is malformed or a sort field is not allowed (400)
     */
    @GET
    public PageResponse<ProductResponse> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("sort") List<String> sort) {
        return catalogService.listProducts(PageQuery.of(page, size, sort));
    }
}
