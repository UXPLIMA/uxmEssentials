package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PageRequest;
import org.jspecify.annotations.NullMarked;

/**
 * One viewer's browsing state for one paged list: the page they are on, the sort they picked out of the spec's
 * offered sorts, and the filters they typed. A paged list source is asked for exactly the page this describes, so
 * this is where the engine remembers what a viewer has done to a list between draws without holding the corpus.
 *
 * <p>Changing what the query returns (a new filter, a different sort) sends the viewer back to page zero. On page
 * seven of the whole corpus, filtering to "shop" would otherwise land on a page past the end of the far shorter
 * filtered result and read as "no shops", so every filter and sort mutation zeroes the page.
 *
 * <p>Lives on a {@link MenuHolder}, which one viewer owns, and follows that holder's plain-field convention: it is
 * touched on the viewer's entity thread as they page and filter, and read once per query when the request is
 * assembled off-thread, never mutated concurrently.
 */
@NullMarked
public final class ListQueryState {

    private final List<String> sorts;

    private final Map<String, String> filters = new HashMap<>();

    private int page;

    private int sortIndex;

    private long total;

    public ListQueryState(List<String> sorts) {
        this.sorts = List.copyOf(Objects.requireNonNull(sorts, "sorts"));
    }

    /** The page the viewer is on, zero-based. */
    public int page() {
        return page;
    }

    /** Moves the viewer to {@code page}, which a nav click has already bounded to the page count. */
    public void page(int page) {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative: " + page);
        }
        this.page = page;
    }

    /** The selected sort, or {@code ""} when the spec offers none, which a paged source reads as its default order. */
    public String sort() {
        return sorts.isEmpty() ? "" : sorts.get(sortIndex);
    }

    /** Advances to the next offered sort, wrapping past the last back to the first; a no-op when none are offered. */
    public void nextSort() {
        if (sorts.isEmpty()) {
            return;
        }
        sortIndex = (sortIndex + 1) % sorts.size();
        page = 0;
    }

    /** Steps to the previous offered sort, wrapping past the first back to the last; a no-op when none are offered. */
    public void previousSort() {
        if (sorts.isEmpty()) {
            return;
        }
        sortIndex = (sortIndex - 1 + sorts.size()) % sorts.size();
        page = 0;
    }

    /** Returns to the first offered sort and to page zero. */
    public void resetSort() {
        sortIndex = 0;
        page = 0;
    }

    /**
     * Sets {@code key} to {@code value}, or clears it when {@code value} is empty so a blank input reverts to the
     * unfiltered corpus rather than matching on emptiness. Either way the viewer returns to page zero.
     */
    public void filter(String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (value.isEmpty()) {
            filters.remove(key);
        } else {
            filters.put(key, value);
        }
        page = 0;
    }

    /** Removes the filter on {@code key} and returns the viewer to page zero. */
    public void clearFilter(String key) {
        Objects.requireNonNull(key, "key");
        filters.remove(key);
        page = 0;
    }

    /** An immutable snapshot of the active filters, keyed as the source expects them. */
    public Map<String, String> filters() {
        return Map.copyOf(filters);
    }

    /** Records the size of the whole filtered corpus the last query reported, the basis for {@link #pageCount}. */
    public void recordTotal(long total) {
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative: " + total);
        }
        this.total = total;
    }

    /** The size of the whole filtered corpus the last query reported. */
    public long total() {
        return total;
    }

    /**
     * How many pages of {@code size} the recorded total fills, never fewer than one so an empty corpus still renders
     * one empty page. Derived from the total the source reported, never from a cached page's length.
     */
    public int pageCount(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("page size must be positive: " + size);
        }
        long pages = (total + size - 1) / size;
        return (int) Math.max(1, pages);
    }

    /** Assembles the request for the current page at {@code size}, carrying the current sort and filters. */
    public PageRequest request(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("page size must be positive: " + size);
        }
        return new PageRequest(page, size, sort(), filters);
    }
}
