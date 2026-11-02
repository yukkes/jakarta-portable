package com.example.app.core.web.paging;

/**
 * One sort key (field name and direction).
 *
 * @param field the API field name; checked against the repository's {@code SORTABLE}
 * @param direction the direction
 */
public record SortOrder(String field, Direction direction) {

    /** A sort direction. {@code PageQuery} accepts it in any case. */
    public enum Direction {
        /** Ascending. */
        ASC,
        /** Descending. */
        DESC
    }
}
