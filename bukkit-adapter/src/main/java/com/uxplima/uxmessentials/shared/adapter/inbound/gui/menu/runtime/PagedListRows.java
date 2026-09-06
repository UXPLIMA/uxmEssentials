package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PagedResult;
import org.jspecify.annotations.NullMarked;

/**
 * Turns one page a paged list source returned into the flat entry list the engine renders: the pinned rows first,
 * then the flow rows trimmed to the content slots left once the pinned rows have claimed theirs. A source that
 * over-returns is a bug in the source, not something to hide, so the overflow is dropped and logged once at warn
 * rather than silently kept: a silently kept overflow would read to the operator as "the viewer saw everything".
 *
 * <p>Both the first-open resolve (the {@code Menus} façade) and a page flip (the {@code MenuListener}) assemble their
 * page through this one method, so the two paths cannot drift on how a page is built or when an overflow is reported.
 */
@NullMarked
public final class PagedListRows {

    private PagedListRows() {}

    /**
     * The page's entries in render order: {@code result}'s pinned rows first, then its flow rows bounded to the
     * {@code size - pinned} content slots that remain. {@code log} receives one {@code event=paged_source_overflowed}
     * warning when the flow overruns that capacity; a page that fits logs nothing.
     */
    public static List<Object> combine(String sourceId, int size, PagedResult<?> result, Logger log) {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(log, "log");
        List<Object> combined = new ArrayList<>(result.pinned());
        combined.addAll(boundedRows(sourceId, size, result.pinned().size(), result.rows(), log));
        return combined;
    }

    private static List<?> boundedRows(String sourceId, int size, int pinned, List<?> rows, Logger log) {
        int capacity = Math.max(0, size - pinned);
        if (rows.size() <= capacity) {
            return rows;
        }
        log.warning("event=paged_source_overflowed id=" + sourceId + " size=" + size + " rows=" + rows.size()
                + " pinned=" + pinned);
        return List.copyOf(rows.subList(0, capacity));
    }
}
