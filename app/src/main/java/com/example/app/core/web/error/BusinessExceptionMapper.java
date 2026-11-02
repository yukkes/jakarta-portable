package com.example.app.core.web.error;

import jakarta.ws.rs.ext.Provider;

/** Turns a {@link BusinessException} into a response. */
@Provider
public class BusinessExceptionMapper extends ApiExceptionMapper<BusinessException> {

    /** Creates the mapper. The runtime discovers it through {@code @Provider} and injects its fields. */
    public BusinessExceptionMapper() {}

    @Override
    protected Outcome classify(BusinessException exception) {
        return Outcome.of(exception.getStatus(), "BUSINESS_ERROR", exception.getMessage());
    }
}
