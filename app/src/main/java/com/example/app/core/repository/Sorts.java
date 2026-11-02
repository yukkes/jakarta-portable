package com.example.app.core.repository;

import com.example.app.core.web.error.BusinessException;
import com.example.app.core.web.paging.SortOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns a sort specification into a JPQL {@code order by} list. Only the fields in each repository's
 * {@code SORTABLE} (constant JPQL paths) can be used, so user input never ends up in a query.
 *
 * <p>{@code fallbackPath} must be a unique column; it is appended to every sort. Sorting only by a column with
 * duplicate values lets tied rows change order between pages, so a row can appear on two pages or on none.
 */
final class Sorts {

    private Sorts() {}

    /**
     * Builds the {@code order by} list for a request.
     *
     * @param sort the requested sort, in order of precedence
     * @param allowed API field name to constant JPQL path
     * @param fallbackPath a unique column, appended unless already requested
     * @param fallbackDirection the direction of the fallback
     * @return the comma-separated {@code order by} items, without the keywords
     * @throws BusinessException if a field not in {@code allowed} is requested (400)
     */
    static String of(
            List<SortOrder> sort,
            Map<String, String> allowed,
            String fallbackPath,
            SortOrder.Direction fallbackDirection) {
        List<String> paths = new ArrayList<>();
        List<String> specs = new ArrayList<>();
        for (SortOrder order : sort) {
            String path = allowed.get(order.field());
            if (path == null) {
                throw BusinessException.badRequest("Field cannot be sorted: " + order.field());
            }
            paths.add(path);
            specs.add(spec(path, order.direction()));
        }
        if (!paths.contains(fallbackPath)) {
            specs.add(spec(fallbackPath, fallbackDirection));
        }
        return String.join(", ", specs);
    }

    /**
     * Builds one {@code order by} item.
     *
     * @param path the JPQL path
     * @param direction the direction
     * @return the path followed by {@code asc} or {@code desc}
     */
    private static String spec(String path, SortOrder.Direction direction) {
        return path + (direction == SortOrder.Direction.ASC ? " asc" : " desc");
    }
}
