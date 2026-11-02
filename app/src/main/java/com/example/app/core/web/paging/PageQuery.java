package com.example.app.core.web.paging;

import com.example.app.core.web.error.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Paging and sorting parameters of a list request.
 *
 * <p>{@code page} is 0-based and {@code size} is 1..{@value #MAX_SIZE}. Out-of-range values are clamped, not rejected.
 * Only the {@code field,asc|desc} format of a sort is checked here; whether the field may be sorted is decided by each
 * repository's {@code SORTABLE}. Without a sort the list is empty and the repository's default order applies.
 *
 * @param page the 0-based page number
 * @param size the page size
 * @param sort the sort keys, earlier ones first; empty for the default order
 */
public record PageQuery(int page, int size, List<SortOrder> sort) {

    /** The page size when the request names none. */
    public static final int DEFAULT_SIZE = 20;
    /** The largest page size; larger requests get this size. */
    public static final int MAX_SIZE = 100;

    /**
     * Creates the query, clamping {@code page} and {@code size} into range; a null sort becomes empty.
     *
     * @param page the 0-based page number; negative values mean 0
     * @param size the page size, clamped to 1..{@value #MAX_SIZE}
     * @param sort the sort keys, or null
     */
    public PageQuery {
        page = Math.max(0, page);
        size = Math.min(Math.max(1, size), MAX_SIZE);
        sort = sort == null ? List.of() : List.copyOf(sort);
    }

    /**
     * Creates a query in the repository's default order.
     *
     * @param page the 0-based page number; negative values mean 0
     * @param size the page size, clamped to 1..{@value #MAX_SIZE}
     */
    public PageQuery(int page, int size) {
        this(page, size, List.of());
    }

    /**
     * Builds the query from the request parameters.
     *
     * @param page the 0-based page number; negative values mean 0
     * @param size the page size, clamped to 1..{@value #MAX_SIZE}
     * @param rawSort values of the {@code sort} parameter (earlier values take precedence)
     * @return the query
     * @throws BusinessException when a sort is malformed (400)
     */
    public static PageQuery of(int page, int size, List<String> rawSort) {
        return new PageQuery(page, size, parseSort(rawSort));
    }

    /**
     * Returns the index of the page's first row, for SQL {@code OFFSET}.
     *
     * @return {@code page * size}
     */
    public long offset() {
        return (long) page * size;
    }

    static List<SortOrder> parseSort(List<String> rawSort) {
        if (rawSort == null) {
            return List.of();
        }
        List<SortOrder> result = new ArrayList<>();
        for (String raw : rawSort) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String[] parts = raw.split(",", -1);
            if (parts.length > 2) {
                throw BusinessException.badRequest("Specify the sort as field,asc|desc: " + raw);
            }
            SortOrder.Direction direction = parts.length == 2 && !parts[1].isBlank()
                    ? parseDirection(parts[1].trim())
                    : SortOrder.Direction.ASC;
            result.add(new SortOrder(parts[0].trim(), direction));
        }
        return result;
    }

    private static SortOrder.Direction parseDirection(String value) {
        try {
            return SortOrder.Direction.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("Specify the sort direction as asc or desc: " + value);
        }
    }
}
