package com.example.app.core.web.paging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.app.core.web.error.BusinessException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageQueryTest {

    @Test
    void clampsPageAndSizeInsteadOfRejecting() {
        assertEquals(0, new PageQuery(-1, 20).page());
        assertEquals(1, new PageQuery(0, 0).size());
        assertEquals(PageQuery.MAX_SIZE, new PageQuery(0, 1_000).size());
        assertEquals(1, new PageQuery(0, -5).size());
    }

    @Test
    void computesOffsetWithoutOverflowingForHugePages() {
        assertEquals(0L, new PageQuery(0, PageQuery.MAX_SIZE).offset());
        assertEquals(
                (long) Integer.MAX_VALUE * PageQuery.MAX_SIZE,
                new PageQuery(Integer.MAX_VALUE, PageQuery.MAX_SIZE).offset());
    }

    @Test
    void leavesTheSortEmptyWhenNoneIsGiven() {
        assertEquals(List.of(), PageQuery.of(0, 20, null).sort());
        assertEquals(List.of(), PageQuery.of(0, 20, List.of(" ", "")).sort());
        assertEquals(
                List.of(), PageQuery.of(0, 20, Arrays.asList((String) null)).sort());
    }

    @Test
    void parsesFieldAndDirection() {
        PageQuery query = PageQuery.of(2, 5, List.of("total,desc", "createdAt", "name, Asc "));

        assertEquals(10L, query.offset());
        assertEquals(
                List.of(
                        new SortOrder("total", SortOrder.Direction.DESC),
                        new SortOrder("createdAt", SortOrder.Direction.ASC),
                        new SortOrder("name", SortOrder.Direction.ASC)),
                query.sort());
        assertEquals(
                List.of(new SortOrder("total", SortOrder.Direction.DESC)),
                PageQuery.of(0, 5, List.of("total,DESC")).sort());
    }

    @Test
    void rejectsMalformedSorts() {
        assertEquals(
                400,
                assertThrows(BusinessException.class, () -> PageQuery.of(0, 5, List.of("id,up")))
                        .getStatus());
        assertEquals(
                400,
                assertThrows(BusinessException.class, () -> PageQuery.of(0, 5, List.of("id,asc,1")))
                        .getStatus());
    }
}
