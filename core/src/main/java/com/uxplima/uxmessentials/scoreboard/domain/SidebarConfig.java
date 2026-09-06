package com.uxplima.uxmessentials.scoreboard.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.display.ConditionContext;

/**
 * The ordered set of {@link SidebarBoard}s a server has authored, and the pure selector that turns a viewer's
 * {@link ConditionContext} into the one board they should see. The boards are held in config order: the order matters
 * because it breaks priority ties (see {@link #select}).
 *
 * <p>The codec builds this from the {@code boards { … }} config block, or, for back-compat with the historical
 * single-board shape, from one implicit {@code default} board wrapping the top-level {@code scoreboard { … }} content
 * with an always-true condition and priority {@code 0}. An unauthored module yields {@link #empty()}: no boards,
 * so {@link #select} returns empty and the renderer shows nothing.
 *
 * @param boards the authored boards in config order (defensively copied)
 */
public record SidebarConfig(List<SidebarBoard> boards) {

    public SidebarConfig {
        Objects.requireNonNull(boards, "boards");
        boards = List.copyOf(boards);
    }

    /** The no-boards default an unauthored module renders from: {@link #select} always returns empty. */
    public static SidebarConfig empty() {
        return new SidebarConfig(List.of());
    }

    /**
     * The board the viewer described by {@code ctx} should see: the highest-{@link SidebarBoard#priority() priority}
     * board whose {@link SidebarBoard#condition() condition} matches, or empty when none match. Ties on priority are
     * broken by config order, the board declared earlier wins, so an operator can rely on the listing order for
     * deterministic selection among equal-priority boards.
     */
    public Optional<SidebarBoard> select(ConditionContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        SidebarBoard best = null;
        for (SidebarBoard board : boards) {
            if (!board.condition().matches(ctx)) {
                continue;
            }
            // Strictly greater keeps the earlier board on a tie, since we scan in config order.
            if (best == null || board.priority() > best.priority()) {
                best = board;
            }
        }
        return Optional.ofNullable(best);
    }
}
