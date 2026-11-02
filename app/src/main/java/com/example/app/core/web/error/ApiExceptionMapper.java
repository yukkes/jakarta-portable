package com.example.app.core.web.error;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;

/**
 * The shared part of the exception mappers. Subclasses only classify an exception into a status, an error code and a
 * message; the body is built here, as the default JSON or as RFC 9457 Problem Details ({@link ProblemDetail}).
 */
abstract class ApiExceptionMapper<E extends Throwable> implements ExceptionMapper<E> {

    @Context
    private HttpHeaders headers;

    @Context
    private UriInfo uriInfo;

    /**
     * Classifies an exception for the response.
     *
     * @param exception the exception being mapped
     * @return the status, error code and message, and the response to build on
     */
    protected abstract Outcome classify(E exception);

    @Override
    public final Response toResponse(E exception) {
        Outcome outcome = classify(exception);
        boolean problemJson = ProblemDetail.requested(headers);
        return outcome.builder()
                .type(ProblemDetail.contentType(problemJson))
                .entity(ProblemDetail.body(
                        outcome.status(), outcome.code(), outcome.message(), problemJson, uriInfo.getRequestUri()))
                .build();
    }

    /**
     * The classification of an exception.
     *
     * @param status the HTTP status
     * @param code the error code of the default format
     * @param message the message for the client
     * @param builder the response being built, which keeps headers such as {@code Allow} on a 405
     */
    protected record Outcome(int status, String code, String message, Response.ResponseBuilder builder) {

        static Outcome of(int status, String code, String message) {
            return new Outcome(status, code, message, Response.status(status));
        }

        static Outcome preserving(Response response, int status, String code, String message) {
            return new Outcome(status, code, message, Response.fromResponse(response));
        }
    }
}
