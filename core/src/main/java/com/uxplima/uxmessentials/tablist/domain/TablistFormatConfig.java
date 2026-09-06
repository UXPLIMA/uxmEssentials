package com.uxplima.uxmessentials.tablist.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.display.ConditionContext;

/**
 * The set of {@link TablistFormat}s a server has authored, and the pure selector that turns a viewer's
 * {@link ConditionContext} into the one format they should get. Mirrors the scoreboard's {@code SidebarConfig}: the
 * formats are held in the order the codec hands them over (sorted by name), so a priority tie resolves to the
 * alphabetically-first format name deterministically across reloads.
 *
 * <p>The codec builds this from the {@code formats { … }} config block, or, for back-compat with the historical
 * single-tablist shape, from one implicit {@code default} format wrapping the top-level {@code tablist { … }} content
 * with an always-true condition, priority {@code 0}, and no name/order override. An unauthored module yields
 * {@link #empty()}: no formats, so {@link #select} returns empty and the renderer clears the tablist.
 *
 * @param formats the authored formats in selection order (defensively copied)
 */
public record TablistFormatConfig(List<TablistFormat> formats) {

    public TablistFormatConfig {
        Objects.requireNonNull(formats, "formats");
        formats = List.copyOf(formats);
    }

    /** The no-formats default an unauthored module renders from: {@link #select} always returns empty. */
    public static TablistFormatConfig empty() {
        return new TablistFormatConfig(List.of());
    }

    /**
     * The format the viewer described by {@code ctx} should get: the highest-{@link TablistFormat#priority() priority}
     * format whose {@link TablistFormat#condition() condition} matches, or empty when none match. Ties on priority are
     * broken by the order the formats are held in, the format earlier in the list wins, which the codec fixes to
     * alphabetical name order, so an operator can rely on a deterministic, reload-stable tie-break among equal-priority
     * formats.
     */
    public Optional<TablistFormat> select(ConditionContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        TablistFormat best = null;
        for (TablistFormat format : formats) {
            if (!format.condition().matches(ctx)) {
                continue;
            }
            // Strictly greater keeps the earlier format on a tie, since we scan in the codec's name-sorted order.
            if (best == null || format.priority() > best.priority()) {
                best = format;
            }
        }
        return Optional.ofNullable(best);
    }
}
