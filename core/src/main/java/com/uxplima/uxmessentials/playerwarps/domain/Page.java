package com.uxplima.uxmessentials.playerwarps.domain;

import java.util.List;
import java.util.Objects;

/**
 * One page of a larger result: the {@code items} on this page plus the {@code totalCount} that match the query
 * across every page, so a caller can render "showing 1-45 of 12,340" and decide whether a next page exists
 * without a second count. The read-model fills this from a bounded {@code LIMIT}/{@code OFFSET} query and a
 * companion count query over the same filter, never by materialising every match.
 *
 * <p>{@code page} is zero-based: page 0 is the first page. {@code items} is defensively copied to an immutable
 * list so a page handed out of the read-model can never be mutated by a caller.
 *
 * @param <T> the element type on the page
 * @param items the elements on this page, at most {@code pageSize} of them
 * @param totalCount how many elements match the query in total, across all pages
 * @param page the zero-based index of this page
 * @param pageSize the maximum number of elements a page holds
 */
public record Page<T>(List<T> items, long totalCount, int page, int pageSize) {

    public Page {
        Objects.requireNonNull(items, "items");
        items = List.copyOf(items);
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount must not be negative: " + totalCount);
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative: " + page);
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be at least 1: " + pageSize);
        }
    }

    /** An empty page for {@code query}'s coordinates: no items and a zero total. */
    public static <T> Page<T> empty(int page, int pageSize) {
        return new Page<>(List.of(), 0L, page, pageSize);
    }

    /** True when at least one more page follows this one: i.e. later matches remain beyond this page's window. */
    public boolean hasNext() {
        return (long) (page + 1) * pageSize < totalCount;
    }

    /** True when this page carries no items. */
    public boolean isEmpty() {
        return items.isEmpty();
    }
}
