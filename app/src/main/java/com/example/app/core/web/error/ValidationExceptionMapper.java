package com.example.app.core.web.error;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.stream.Collectors;

/** Turns Bean Validation violations into 400 {@code VALIDATION_ERROR}. The message includes the field names. */
@Provider
public class ValidationExceptionMapper extends ApiExceptionMapper<ConstraintViolationException> {

    /** Creates the mapper. The runtime discovers it through {@code @Provider} and injects its fields. */
    public ValidationExceptionMapper() {}

    @Override
    protected Outcome classify(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining(", "));
        return Outcome.of(Response.Status.BAD_REQUEST.getStatusCode(), "VALIDATION_ERROR", message);
    }
}
