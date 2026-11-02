package com.example.app.core.web.paging;

import java.util.List;

/**
 * The response of a list endpoint. {@code page} is 0-based.
 *
 * @param <T> the row type
 * @param content the rows of this page
 * @param page the 0-based page number
 * @param size the page size
 * @param totalElements the number of rows on all pages
 * @param totalPages the number of pages; 0 when there are no rows
 * @param first whether this is the first page
 * @param last whether this is the last page (also when there are no rows)
 * @param hasNext whether a next page exists
 * @param hasPrevious whether a previous page exists
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean hasNext,
        boolean hasPrevious) {

    /**
     * Creates the response of one page.
     *
     * @param <T> the row type
     * @param content the rows of this page (at most {@code query.size()})
     * @param query the page that was read
     * @param totalElements the count for the same condition; it decides {@code totalPages} and {@code last}
     * @return the response with the totals computed
     */
    public static <T> PageResponse<T> of(List<T> content, PageQuery query, long totalElements) {
        int totalPages = totalElements == 0
                ? 0
                : (int) Math.min(Integer.MAX_VALUE, (totalElements + query.size() - 1) / query.size());
        boolean first = query.page() == 0;
        boolean last = totalPages == 0 || query.page() >= totalPages - 1;
        return new PageResponse<>(
                content, query.page(), query.size(), totalElements, totalPages, first, last, !last, !first);
    }
}
