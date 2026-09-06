package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval;

import java.util.List;
import java.util.Objects;

/**
 * One page of rows plus the size of the whole filtered corpus. The total is what lets the engine render
 * {@code %max_page%} and decide whether a next-page button applies, without ever holding more than a page.
 *
 * <p>{@code pinned} rows claim a fixed content slot through {@link PinnedEntry} and sit outside the page's flow
 * a sponsored entry that must appear on every page, for instance. They are not counted in {@code totalCount}.
 *
 * <p>A page has a fixed capacity: because pinned entries occupy content slots too, {@code rows.size() + pinned.size()}
 * must not exceed the {@code size} the {@link PageRequest} asked for. A source that returns more than fits has the
 * overflow dropped by the engine, which logs it at warn rather than discarding it silently.
 *
 * <p>Not to be confused with {@code Pagination.Page}, which is the engine's slot-placement result for an
 * in-memory list. This type is the data a source returns; that type is where the renderer puts it.
 */
public record PagedResult<T>(List<T> rows, long totalCount, List<T> pinned) {

    public PagedResult {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        pinned = List.copyOf(Objects.requireNonNull(pinned, "pinned"));
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount must not be negative: " + totalCount);
        }
    }

    /** A page with no pinned rows, the common case. */
    public static <T> PagedResult<T> of(List<T> rows, long totalCount) {
        return new PagedResult<>(rows, totalCount, List.of());
    }

    /** How many pages of {@code size} the corpus fills; never fewer than one, so an empty list still renders. */
    public int pageCount(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("page size must be positive: " + size);
        }
        long pages = (totalCount + size - 1) / size;
        return (int) Math.max(1, pages);
    }
}
