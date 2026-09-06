package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the bare-{@code /eco} admin hub with the menu engine and opens it. A three-row panel with three
 * entries. [Manage a player] opens the shared {@link PlayerPickerView} and routes the picked target to the
 * per-player {@link EconomyTargetMenu manage screen}, [Server-wide] opens the {@link EconomyBulkMenu bulk screen},
 * and [Transaction history] opens the global transaction log, plus a close button. The raw
 * {@code /eco give|take|set|reset …} subcommands are untouched; this hub is only the bare-root opener.
 *
 * <p>The hub carries the viewer reference as its menu subject, so the three opens reach the live viewer without
 * the renderer touching a port. The picker's offline resolver is backed by {@link PlayerLookup}, so a staff
 * member can type an offline name the head grid does not show and still manage that player's wallet. The picker
 * stays a bespoke view (a transitional cross-runtime seam); the bulk and history opens both route through the
 * already-engine menus. Every visible string resolves from the economy catalog.
 */
@NullMarked
public final class EconomyAdminMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "economy-admin";

    private static final String SPEC_RESOURCE = "modules/economy/gui/economy-admin.conf";
    private static final int ROWS = 3;

    private final Menus menus;
    private final Scheduler scheduler;
    private final PlayerPickerView picker;
    private final PlayerLookup players;
    private final EconomyTargetMenu targetMenu;
    private final EconomyBulkMenu bulkMenu;
    private final TransactionsHistoryMenu historyView;

    public EconomyAdminMenu(
            Menus menus,
            Scheduler scheduler,
            PlayerPickerView picker,
            PlayerLookup players,
            EconomyTargetMenu targetMenu,
            EconomyBulkMenu bulkMenu,
            TransactionsHistoryMenu historyView) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.picker = Objects.requireNonNull(picker, "picker");
        this.players = Objects.requireNonNull(players, "players");
        this.targetMenu = Objects.requireNonNull(targetMenu, "targetMenu");
        this.bulkMenu = Objects.requireNonNull(bulkMenu, "bulkMenu");
        this.historyView = Objects.requireNonNull(historyView, "historyView");
    }

    /** Register the three hub actions the spec names, and the spec itself. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.action("economy:admin-manage", this::openPicker);
        bindings.action("economy:admin-bulk", this::openBulk);
        bindings.action("economy:admin-history", this::openGlobalHistory);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, ROWS, log));
    }

    /** Open the admin hub for {@code viewer}; the viewer reference is the menu subject. */
    public void open(Player viewer, PlayerRef viewerRef) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(viewerRef, "viewerRef");
        menus.open(viewerRef, SPEC_ID, viewerRef);
    }

    /** Open the shared player picker; the picked target routes to the per-player manage screen, as before. */
    private void openPicker(MenuActionContext ctx) {
        Player viewer = ctx.player();
        PlayerRef viewerRef = ctx.viewer();
        PlayerPickerView.Request request = new PlayerPickerView.Request(
                EconomyMessageKey.ECO_ADMIN_GUI_PICK_TITLE,
                target -> targetMenu.open(viewer, viewerRef, target),
                this::resolveOffline,
                EconomyMessageKey.ECO_ADMIN_TARGET_UNKNOWN);
        picker.open(viewer, viewerRef, request);
    }

    private Optional<PlayerRef> resolveOffline(String name) {
        return players.findByName(name);
    }

    /** Open the server-wide bulk screen through the engine, exactly as the old hub did. */
    private void openBulk(MenuActionContext ctx) {
        bulkMenu.open(ctx.player(), ctx.viewer());
    }

    /** Open the global transaction-history list, exactly as the old hub did. */
    private void openGlobalHistory(MenuActionContext ctx) {
        PlayerRef viewerRef = ctx.viewer();
        scheduler.onEntity(viewerRef, () -> historyView.open(viewerRef, null, "Global"));
    }
}
