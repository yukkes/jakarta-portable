package com.example.app.core.batch;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;

/**
 * One line of the order import CSV.
 *
 * <p>The format is plain comma-separated {@code customerCode,productCode,quantity[,orderDate]}; quotes are not
 * supported. Blank lines and lines starting with {@code #} are skipped. A line containing a quote is an error, which
 * beats splitting it in the wrong place and importing it as a different order.
 *
 * @param customerCode the customer code
 * @param productCode the product code
 * @param quantity the quantity; {@code OrderService} requires at least 1
 * @param orderDate the order date, or null for today
 */
public record ImportCsvLine(String customerCode, String productCode, int quantity, LocalDate orderDate) {

    /**
     * Parses one line.
     *
     * @param line the line, not ignorable
     * @return the parsed line
     * @throws CsvFormatException if the line is malformed
     */
    public static ImportCsvLine parse(String line) {
        if (line.indexOf('"') >= 0) {
            throw new CsvFormatException(
                    "Lines with a quote (\") are not supported: use customerCode,productCode,quantity[,orderDate]");
        }
        String[] columns = line.split(",", -1);
        if (columns.length < 3) {
            throw new CsvFormatException("Three columns are required: customerCode,productCode,quantity");
        }
        if (columns.length > 4) {
            throw new CsvFormatException("Too many columns (at most 4: customerCode,productCode,quantity[,orderDate])");
        }

        String customerCode = columns[0].trim();
        String productCode = columns[1].trim();
        if (customerCode.isEmpty() || productCode.isEmpty()) {
            throw new CsvFormatException("Customer code and product code are required");
        }

        int quantity;
        try {
            quantity = Integer.parseInt(columns[2].trim());
        } catch (NumberFormatException exception) {
            throw new CsvFormatException("Quantity is not an integer");
        }

        LocalDate orderDate = null;
        if (columns.length == 4 && !columns[3].isBlank()) {
            try {
                orderDate = LocalDate.parse(columns[3].trim());
            } catch (DateTimeParseException exception) {
                throw new CsvFormatException("Specify the order date as YYYY-MM-DD");
            }
        }

        return new ImportCsvLine(customerCode, productCode, quantity, orderDate);
    }

    /**
     * Tells whether a line is blank or a comment. Such lines are skipped, not errors.
     *
     * @param line the line
     * @return true if the line is to be skipped
     */
    public static boolean isIgnorable(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }

    /**
     * Returns the deduplication key of the first occurrence of a line in a file.
     *
     * @param source the file name
     * @param line the line
     * @return the key, 64 hexadecimal characters
     */
    public static String importKey(String source, String line) {
        return importKey(source, line, 1);
    }

    /**
     * Returns the deduplication key of a line. It is derived from the file name, the line content and which occurrence
     * of identical lines this is (1-based), so a rerun recognizes imported lines and two identical lines are two
     * orders.
     *
     * <p>The line number is not part of the key, so adding comments or lines above never makes later lines look new.
     * The key of the first occurrence keeps the earlier format, so existing import records stay valid.
     *
     * @param source the file name
     * @param line the line
     * @param occurrence which occurrence of identical lines this is, from 1
     * @return 64 hexadecimal characters (the length of the {@code import_key} column)
     * @throws IllegalArgumentException if {@code occurrence} is below 1
     */
    public static String importKey(String source, String line, int occurrence) {
        if (occurrence < 1) {
            throw new IllegalArgumentException("occurrence starts at 1: " + occurrence);
        }
        String material = source + "\n" + line.strip() + (occurrence == 1 ? "" : "\n#" + occurrence);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
