package com.example.app.core.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ImportCsvLineTest {

    @Test
    void readsTheRequiredColumns() {
        ImportCsvLine row = ImportCsvLine.parse("C001, P002 ,3");

        assertEquals("C001", row.customerCode());
        assertEquals("P002", row.productCode());
        assertEquals(3, row.quantity());
        assertNull(row.orderDate());
    }

    @Test
    void readsTheOptionalOrderDate() {
        assertEquals(
                LocalDate.of(2020, 1, 15),
                ImportCsvLine.parse("C001,P002,1,2020-01-15").orderDate());
        // A trailing comma means "no order date (today)", not an invalid date.
        assertNull(ImportCsvLine.parse("C001,P002,1,").orderDate());
        assertNull(ImportCsvLine.parse("C001,P002,1, ").orderDate());
    }

    @Test
    void skipsBlankAndCommentLines() {
        assertTrue(ImportCsvLine.isIgnorable(""));
        assertTrue(ImportCsvLine.isIgnorable("   "));
        assertTrue(ImportCsvLine.isIgnorable("# customerCode,productCode,quantity"));
        assertFalse(ImportCsvLine.isIgnorable("C001,P002,1"));
    }

    @Test
    void rejectsQuotedFieldsInsteadOfMisreadingThem() {
        // A naive split(",") would read it as an order for a different product code.
        CsvFormatException exception =
                assertThrows(CsvFormatException.class, () -> ImportCsvLine.parse("\"C001\",\"P002\",1"));
        assertTrue(exception.getMessage().contains("quote"));
    }

    @Test
    void rejectsRowsWithTheWrongShape() {
        assertThrows(CsvFormatException.class, () -> ImportCsvLine.parse("C001,P002"));
        assertThrows(CsvFormatException.class, () -> ImportCsvLine.parse("C001,P002,1,2020-01-15,extra"));
        assertThrows(CsvFormatException.class, () -> ImportCsvLine.parse(",P002,1"));
        assertThrows(CsvFormatException.class, () -> ImportCsvLine.parse("C001,,1"));
    }

    @Test
    void rejectsValuesThatAreNotNumbersOrDates() {
        assertThrows(CsvFormatException.class, () -> ImportCsvLine.parse("C001,P002,three"));
        assertThrows(CsvFormatException.class, () -> ImportCsvLine.parse("C001,P002,1,2020/01/15"));
    }

    @Test
    void importKeyIsStableAndContentAddressed() {
        String key = ImportCsvLine.importKey("/data/orders-import.csv", "C001,P002,2");

        assertEquals(key, ImportCsvLine.importKey("/data/orders-import.csv", "C001,P002,2"));
        // Trailing whitespace is line-ending noise, not content.
        assertEquals(key, ImportCsvLine.importKey("/data/orders-import.csv", "C001,P002,2  "));
        assertEquals(64, key.length());
        assertTrue(key.matches("[0-9a-f]{64}"));

        // A rerun of the same file does not import it, but a corrected line is imported.
        assertNotEquals(key, ImportCsvLine.importKey("/data/orders-import.csv", "C001,P002,3"));
        // The same line in a different file is a different import.
        assertNotEquals(key, ImportCsvLine.importKey("other.csv", "C001,P002,2"));
    }

    @Test
    void importKeySeparatesTheLineNumberFromTheContent() {
        // Same content, same key even if the position changes. Adding a comment above does not import twice.
        assertEquals(ImportCsvLine.importKey("a.csv", "C001,P002,2"), ImportCsvLine.importKey("a.csv", "C001,P002,2"));
        // The separators between file name and line keep different lines from sharing a key.
        assertNotEquals(
                ImportCsvLine.importKey("a.csv", "C001,P002,2"), ImportCsvLine.importKey("a.csv,C001,P002,2", ""));
    }

    @Test
    void identicalRowsInOneFileGetDistinctKeys() {
        // Two lines in which the same customer orders the same product twice are 2 orders, not a duplicate.
        String first = ImportCsvLine.importKey("a.csv", "C001,P002,2", 1);
        String second = ImportCsvLine.importKey("a.csv", "C001,P002,2", 2);

        assertNotEquals(first, second);
        assertNotEquals(second, ImportCsvLine.importKey("a.csv", "C001,P002,2", 3));
        // The first key equals the key an earlier version wrote to order_import_log.
        assertEquals(ImportCsvLine.importKey("a.csv", "C001,P002,2"), first);
        assertThrows(IllegalArgumentException.class, () -> ImportCsvLine.importKey("a.csv", "C001,P002,2", 0));
    }
}
