package com.example.app.core.web;

import com.example.app.api.model.BatchExecutionResponse;
import com.example.app.api.model.BatchStartResponse;
import com.example.app.core.service.BatchService;
import com.example.app.core.web.auth.RequireRole;
import com.example.app.core.web.error.BusinessException;
import jakarta.inject.Inject;
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

/** Starts, inspects and restarts batch jobs (ADMIN only). Starting is asynchronous: 202 with the execution ID. */
@RequireRole("ADMIN")
@Path("/batch")
@Produces({MediaType.APPLICATION_JSON, "application/problem+json"})
public class BatchResource {

    @Inject
    private BatchService batchService;

    @Context
    private UriInfo uriInfo;

    /** Creates the resource. The runtime creates one per request and injects its fields. */
    public BatchResource() {}

    /**
     * Starts invoice generation for a month. It runs asynchronously; poll the {@code Location} for its state.
     *
     * <p>Orders already invoiced are skipped, so starting the same month twice never bills twice.
     *
     * @param month the billing month, {@code yyyy-MM}; null or empty means last month
     * @return 202 with the execution and its {@code Location}
     * @throws BusinessException if the month is malformed (400)
     */
    @POST
    @Path("/invoices")
    public Response startInvoiceJob(@QueryParam("month") String month) {
        BatchStartResponse started = batchService.startInvoiceJob(month);
        return accepted(started);
    }

    /**
     * Starts the order CSV import. It runs asynchronously; poll the {@code Location} for its counts.
     *
     * <p>Lines already imported are skipped, so a rerun imports only the rest.
     *
     * @param inputFile a bare {@code .csv} name in {@code data/import/}; null or empty means the bundled sample
     * @return 202 with the execution and its {@code Location}
     * @throws BusinessException if the name is not allowed or the file does not exist (400)
     */
    @POST
    @Path("/imports")
    public Response startImportJob(@QueryParam("inputFile") String inputFile) {
        BatchStartResponse started = batchService.startImportJob(inputFile);
        return accepted(started);
    }

    /**
     * Returns the latest executions of one job or of both, newest first.
     *
     * @param job {@code invoiceJob} or {@code orderImportJob}; null or empty means both
     * @param count how many to return, clamped to 1..50
     * @return the executions, without step details
     */
    @GET
    @Path("/executions")
    public List<BatchExecutionResponse> list(
            @QueryParam("job") String job, @QueryParam("count") @DefaultValue("10") int count) {
        return batchService.listExecutions(job, count);
    }

    /**
     * Returns one execution with its step details.
     *
     * @param id the execution ID
     * @return the execution
     * @throws BusinessException if it does not exist (404)
     */
    @GET
    @Path("/executions/{id}")
    public BatchExecutionResponse get(@PathParam("id") Long id) {
        return batchService.getExecution(id);
    }

    /**
     * Restarts a failed or stopped execution from its last checkpoint, as a new execution.
     *
     * @param id the execution ID
     * @return 202 with the new execution and its {@code Location}
     * @throws BusinessException if it does not exist (404), or completed or is not the latest of its instance (409)
     */
    @POST
    @Path("/executions/{id}/restart")
    public Response restart(@PathParam("id") Long id) {
        long executionId = batchService.restart(id);
        BatchExecutionResponse execution = batchService.getExecution(executionId);
        BatchStartResponse started = new BatchStartResponse();
        started.setExecutionId(executionId);
        started.setJobName(execution.getJobName());
        started.setStatus(execution.getStatus());
        return accepted(started);
    }

    private Response accepted(BatchStartResponse started) {
        URI location = uriInfo.getBaseUriBuilder()
                .path("batch")
                .path("executions")
                .path(started.getExecutionId().toString())
                .build();
        return Response.accepted(location).entity(started).build();
    }
}
