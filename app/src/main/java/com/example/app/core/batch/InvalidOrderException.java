package com.example.app.core.batch;

/** An order that cannot be invoiced. The invoice job skips it (skippable-exception-classes in invoiceJob.xml). */
public class InvalidOrderException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message why the order cannot be invoiced, naming the order
     */
    public InvalidOrderException(String message) {
        super(message);
    }
}
