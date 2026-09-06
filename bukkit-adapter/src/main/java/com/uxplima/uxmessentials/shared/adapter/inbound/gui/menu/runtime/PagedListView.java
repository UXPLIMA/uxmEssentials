package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import org.jspecify.annotations.NullMarked;

/**
 * An immutable snapshot of one paged list's paging, taken on the viewer's entity thread when the render context is
 * assembled and carried to the renderer on the {@link MenuContext}. Its presence in {@link MenuContext#pagedViews()}
 * is the engine's signal that a list-backed item's rows are already exactly one page. The renderer places them
 * without re-slicing, since slicing an already-paged result at the viewer's page index would show page zero of a
 * later page, and it carries the {@code %page%}/{@code %max_page%} the static page indicator reads, derived from the
 * {@link ListQueryState}'s recorded corpus total rather than the rendered page's length.
 */
@NullMarked
public record PagedListView(int page, long totalCount, int size) {

    public PagedListView {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative: " + page);
        }
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount must not be negative: " + totalCount);
        }
        if (size <= 0) {
            throw new IllegalArgumentException("page size must be positive: " + size);
        }
    }

    /**
     * How many pages of {@link #size} the recorded total fills, never fewer than one so an empty corpus still renders
     * one empty page. Mirrors {@link ListQueryState#pageCount(int)} so a snapshot and its live state agree.
     */
    public int pageCount() {
        long pages = (totalCount + size - 1) / size;
        return (int) Math.max(1, pages);
    }
}
