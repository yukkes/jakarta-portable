package com.example.app.core.web.error;

import jakarta.json.JsonException;
import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The catch-all for exceptions no other mapper handles. Clients never see the runtime's default error page (stack
 * traces, server versions). A 500 carries an errorId so it can be matched against the log.
 */
@Provider
public class UnhandledExceptionMapper extends ApiExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(UnhandledExceptionMapper.class.getName());

    /** Creates the mapper. The runtime discovers it through {@code @Provider} and injects its fields. */
    public UnhandledExceptionMapper() {}

    @Override
    protected Outcome classify(Throwable exception) {
        if (exception instanceof WebApplicationException web) {
            // Keep the status and headers (such as Allow on a 405) and replace only the body. The original message
            // may contain internal details such as the request URL.
            int status = web.getResponse().getStatus();
            Response.Status known = Response.Status.fromStatusCode(status);
            return Outcome.preserving(
                    web.getResponse(),
                    status,
                    known != null ? known.name() : "HTTP_ERROR",
                    known != null ? known.getReasonPhrase() : "HTTP " + status);
        }

        if (causedByJson(exception)) {
            // The body is not valid JSON. RESTEasy Classic wraps it in a ProcessingException while Quarkus REST
            // throws the JsonbException as-is, so walk the chain of causes.
            return Outcome.of(
                    Response.Status.BAD_REQUEST.getStatusCode(), "BAD_REQUEST", "The request body is not valid JSON");
        }

        String errorId = UUID.randomUUID().toString();
        LOG.log(Level.SEVERE, "unhandled exception, errorId=" + errorId, exception);
        return Outcome.of(
                Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                "INTERNAL_ERROR",
                "An internal error occurred (errorId=" + errorId + ")");
    }

    private boolean causedByJson(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof JsonbException || cause instanceof JsonException) {
                return true;
            }
        }
        return false;
    }
}
