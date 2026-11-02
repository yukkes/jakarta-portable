package com.example.app.core.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.app.core.web.error.BusinessException;
import com.example.app.core.web.paging.SortOrder;
import com.example.app.core.web.paging.SortOrder.Direction;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SortsTest {

    private static final Map<String, String> SORTABLE =
            Map.of("id", "o.id", "status", "o.status", "orderDate", "o.orderDate");

    @Test
    void usesTheFallbackWhenNothingIsRequested() {
        assertEquals("o.id desc", Sorts.of(List.of(), SORTABLE, "o.id", Direction.DESC));
    }

    @Test
    void appendsTheUniqueFallbackAsATieBreaker() {
        // Many orders share the same status, so without the ID offset paging is not stable.
        assertEquals(
                "o.status asc, o.orderDate desc, o.id desc",
                Sorts.of(
                        List.of(new SortOrder("status", Direction.ASC), new SortOrder("orderDate", Direction.DESC)),
                        SORTABLE,
                        "o.id",
                        Direction.DESC));
    }

    @Test
    void doesNotRepeatTheUniqueColumnWhenItIsRequested() {
        assertEquals(
                "o.id asc", Sorts.of(List.of(new SortOrder("id", Direction.ASC)), SORTABLE, "o.id", Direction.DESC));
    }

    @Test
    void rejectsAFieldOutsideTheWhitelist() {
        assertThrows(
                BusinessException.class,
                () -> Sorts.of(List.of(new SortOrder("password", Direction.ASC)), SORTABLE, "o.id", Direction.DESC));
    }

    @Test
    void rejectsAnInjectionAttempt() {
        assertThrows(
                BusinessException.class,
                () -> Sorts.of(
                        List.of(new SortOrder("id; delete from Order o", Direction.ASC)),
                        SORTABLE,
                        "o.id",
                        Direction.DESC));
    }
}
