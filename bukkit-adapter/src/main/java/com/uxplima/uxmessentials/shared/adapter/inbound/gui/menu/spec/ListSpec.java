package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.List;
import java.util.Objects;

/**
 * A list-backed item: the engine asks the {@code source} reference for a collection of entries and stamps the
 * {@code template} item once per entry, expanding a single spec block into a paginated grid.
 *
 * <p>{@code pageSize} and {@code sorts} are the knobs a paged source reads to page and order its corpus down at the
 * store rather than in memory. A {@code pageSize} of {@code 0} means "derive the page from the item's slot count",
 * which is the historic behaviour every in-memory list still gets; {@code sorts} is the ordered list of sort keys a
 * paged source offers (empty means its default order). Both are inert for a plain in-memory source, which hands its
 * whole corpus over for the engine to slice. The binding validator rejects a spec that sets them there so the
 * mismatch is a loud configuration error rather than a knob that silently does nothing.
 */
public record ListSpec(Ref source, MenuItemSpec template, int pageSize, List<String> sorts) {

    public ListSpec {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(template, "template");
        if (pageSize < 0) {
            throw new IllegalArgumentException("pageSize must be >= 0: " + pageSize);
        }
        sorts = List.copyOf(Objects.requireNonNull(sorts, "sorts"));
    }

    /**
     * The historic two-argument shape, kept so a caller that does not page. The loader's default branch and the test
     * fixtures, reads plainly. It delegates with a {@code 0} page size (derive from slots) and no sorts, so such a
     * list behaves exactly as it did before those knobs existed.
     */
    public ListSpec(Ref source, MenuItemSpec template) {
        this(source, template, 0, List.of());
    }
}
