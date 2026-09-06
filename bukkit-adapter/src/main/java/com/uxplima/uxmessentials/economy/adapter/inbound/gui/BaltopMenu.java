package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.adapter.outbound.BaltopSnapshots;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the top-balances leaderboard with the menu engine and opens it. A paginated grid of one skull per
 * ranked owner showing the rank, the player and the formatted balance; a click does nothing, because the
 * leaderboard is a read-only view.
 *
 * <p>The ranked rows come from the per-currency baltop snapshot cache (a lock-free, Bukkit-free read) so the
 * {@code economy:baltop} list source resolves them straight off the tick thread for the opened currency, which the
 * menu carries as its subject. Each {@code baltop_*} placeholder fills a cell from the bound row, prefixed so they
 * never collide with the generic engine tokens or another menu's fields. The title's {@code {baltop_currency}}
 * argument reads that same subject, so a {@code /baltop coins} window names the currency it ranks. Every label
 * resolves from the economy catalog, so no user-facing text lives here. The geometry mirrors the original
 * leaderboard: a grid across the top five rows and the nav arrows and close button on the bottom row.
 */
@NullMarked
public final class BaltopMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "economy-baltop";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/economy/gui/economy-baltop.conf";

    /** Mirrors the per-page cap the original leaderboard read, so the off-thread read stays bounded. */
    private static final int TOP_LIMIT = 100;

    private final Menus menus;
    private final BaltopSnapshots snapshots;
    private final EconomyNotifier notifier;

    public BaltopMenu(Menus menus, BaltopSnapshots snapshots, EconomyNotifier notifier) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Register the list source, the per-entry and title placeholders, and the spec itself; called once at wiring. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("economy:baltop", this::rows);
        bindings.placeholder("baltop_currency", ctx -> subject(ctx).plural());
        bindings.placeholder("baltop_rank", ctx -> Integer.toString(entry(ctx).rank()));
        bindings.placeholder("baltop_player", ctx -> entry(ctx).row().owner().name());
        bindings.placeholder(
                "baltop_amount", ctx -> notifier.amount(entry(ctx).row().balance()));
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /** Open the leaderboard for {@code viewer}, ranking the snapshot cache for {@code currency}. */
    public void open(PlayerRef viewer, Currency currency) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(currency, "currency");
        menus.open(viewer, SPEC_ID, currency);
    }

    /**
     * The ranked rows for the opened currency, read off-thread from the snapshot cache by the engine and ranked
     * one-based in descending-balance order (the order the snapshot already returns), so the grid renders without
     * re-sorting.
     */
    private List<RankedRow> rows(MenuContext ctx) {
        List<BaltopRow> top = snapshots.top(subject(ctx), TOP_LIMIT);
        List<RankedRow> ranked = new ArrayList<>(top.size());
        for (int i = 0; i < top.size(); i++) {
            ranked.add(new RankedRow(i + 1, top.get(i)));
        }
        return ranked;
    }

    private Currency subject(MenuContext ctx) {
        return ctx.subject(Currency.class);
    }

    private RankedRow entry(MenuContext ctx) {
        return ctx.entry(RankedRow.class);
    }

    /**
     * One leaderboard cell: a one-based rank and its {@link BaltopRow}. The list source produces these, so each
     * placeholder reads the rank and the row directly off the bound entry.
     *
     * @param rank the one-based leaderboard position
     * @param row the owner and balance at that position
     */
    public record RankedRow(int rank, BaltopRow row) {

        public RankedRow {
            Objects.requireNonNull(row, "row");
        }
    }
}
