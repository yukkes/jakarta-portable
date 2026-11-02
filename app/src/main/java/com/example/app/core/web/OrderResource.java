package com.example.app.core.web;

import com.example.app.api.model.CreateOrderRequest;
import com.example.app.api.model.OrderResponse;
import com.example.app.core.service.OrderService;
import com.example.app.core.web.auth.RequireRole;
import com.example.app.core.web.error.BusinessException;
import com.example.app.core.web.paging.PageQuery;
import com.example.app.core.web.paging.PageResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;

/** Listing, reading, creating, confirming and cancelling orders. */
@RequireRole({"USER", "ADMIN"})
@Path("/orders")
@Produces({MediaType.APPLICATION_JSON, "application/problem+json"})
public class OrderResource {

    @Inject
    private OrderService orderService;

    @Context
    private UriInfo uriInfo;

    /** Creates the resource. The runtime creates one per request and injects its fields. */
    public OrderResource() {}

    /**
     * Returns one page of orders with their lines, newest first unless {@code sort} says otherwise.
     *
     * @param status the status to filter by, in any case; null or empty means every status
     * @param page the 0-based page number; negative values mean 0
     * @param size the page size, clamped to 1..100
     * @param sort {@code field,asc|desc} values, earlier ones first
     * @return the page and the totals
     * @throws BusinessException if the status is unknown, a parameter is malformed or a sort field is not allowed (400)
     */
    @GET
    public PageResponse<OrderResponse> list(
            @QueryParam("status") String status,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("sort") List<String> sort) {
        return orderService.listOrders(status, PageQuery.of(page, size, sort));
    }

    /**
     * Returns one order with its lines.
     *
     * @param id the order ID
     * @return the order
     * @throws BusinessException if it does not exist (404)
     */
    @GET
    @Path("/{id}")
    public OrderResponse get(@PathParam("id") Long id) {
        return orderService.getOrder(id);
    }

    /**
     * Creates a DRAFT order at the products' current prices. Stock is not touched until the order is confirmed.
     *
     * @param request the customer, the lines and optionally the order date (today if absent); validated, 400 if invalid
     * @return 201 with the order and its {@code Location}
     * @throws BusinessException if the customer or a product does not exist (404)
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Valid CreateOrderRequest request) {
        OrderResponse order = orderService.createOrder(request);
        URI location = uriInfo.getBaseUriBuilder()
                .path("orders")
                .path(order.getId().toString())
                .build();
        return Response.created(location).entity(order).build();
    }

    /**
     * Confirms a DRAFT order and takes its quantities out of stock.
     *
     * @param id the order ID
     * @return the confirmed order
     * @throws BusinessException if it does not exist (404); if it is not DRAFT, the stock is short, or another request
     *     changed the order or a product meanwhile, so the client may retry (409)
     */
    @POST
    @Path("/{id}/confirm")
    public OrderResponse confirm(@PathParam("id") Long id) {
        return orderService.confirmOrder(id);
    }

    /**
     * Cancels an order and, if it was CONFIRMED, puts its quantities back into stock.
     *
     * @param id the order ID
     * @return the cancelled order
     * @throws BusinessException if it does not exist (404); if it is already cancelled or invoiced, or on a concurrent
     *     update (409)
     */
    @POST
    @Path("/{id}/cancel")
    public OrderResponse cancel(@PathParam("id") Long id) {
        return orderService.cancelOrder(id);
    }
}
