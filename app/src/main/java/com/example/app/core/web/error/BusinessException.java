package com.example.app.core.web.error;

/** A business rule violation. {@link BusinessExceptionMapper} returns it as {@code BUSINESS_ERROR} with a status. */
public class BusinessException extends RuntimeException {

    /** The HTTP status of the response. */
    private final int status;

    /**
     * Creates a business rule violation; prefer the factory methods.
     *
     * @param status the HTTP status: 400, 404 or 409
     * @param message the message for the client; it must not contain internal details
     */
    public BusinessException(int status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Returns the HTTP status of the response.
     *
     * @return 400, 404 or 409
     */
    public int getStatus() {
        return status;
    }

    /**
     * Creates a 400: the request itself is invalid (a malformed parameter, an unknown sort field, a rule the input
     * breaks).
     *
     * @param message the message for the client
     * @return the exception
     */
    public static BusinessException badRequest(String message) {
        return new BusinessException(400, message);
    }

    /**
     * Creates a 404: a referenced resource does not exist.
     *
     * @param message the message for the client
     * @return the exception
     */
    public static BusinessException notFound(String message) {
        return new BusinessException(404, message);
    }

    /**
     * Creates a 409: the request is valid but the current state forbids it (a wrong status, short stock, a concurrent
     * update).
     *
     * @param message the message for the client
     * @return the exception
     */
    public static BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }
}
