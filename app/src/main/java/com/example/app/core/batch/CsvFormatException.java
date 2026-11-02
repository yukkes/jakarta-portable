package com.example.app.core.batch;

/** A CSV line that cannot be read as an order. The message goes to the log, so it never contains the line itself. */
public class CsvFormatException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message why the line was rejected; it is logged, so it never quotes the line
     */
    public CsvFormatException(String message) {
        super(message);
    }
}
