package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Assembles the moderation management GUI's three views. The active-punishments list, the per-punishment
 * detail/manage view, and a target's read-only history, and threads the navigation between them. The list
 * opens by default ({@code /mod} with no args and the {@code /uxmess gui} hub entry); a click drills into the
 * detail view, which can revoke (confirm-gated) or open the clicked target's history. The list is the
 * engine-rendered {@link ModerationActiveMenu}; the detail/manage view stays bespoke (an {@code EntityEditorView}
 * property grid). Layouts come from the module's {@code gui/*.conf} (operator-editable, code default otherwise);
 * every visible string is a catalog key. The views are constructed once here and reused for every viewer.
 *
 * <p>The list↔detail cycle is broken with a one-slot holder: the detail view's back/history callbacks read the
 * engine list (and re-resolve a viewer from the live player) at click time, by which point the list is built. This
 * mirrors the worlds list→bespoke-editor wiring.
 */
@NullMarked
public final class ModerationGuiViews {

    private final ModerationActiveMenu list;
    private final ModerationHistoryMenu historyMenu;

    private ModerationGuiViews(ModerationActiveMenu list, ModerationHistoryMenu historyMenu) {
        this.list = list;
        this.historyMenu = historyMenu;
    }

    /** Build the three views over the existing use cases, the module's GUI layouts and the menu engine. */
    public static ModerationGuiViews create(
            GuiText guiText,
            Scheduler scheduler,
            ModerationServices services,
            ModerationRepository repository,
            PlayerLookup players,
            Messages messages,
            ModerationHistoryMenu historyMenu,
            Clock clock,
            GuiLayouts layouts,
            Menus menus,
            MenuBindings menuBindings,
            Path dataFolder,
            Logger log) {
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(historyMenu, "historyMenu");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(layouts, "layouts");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");

        EntityEditorLayout detailLayout =
                layouts.loadEntityEditor("moderation", "punishment-detail", detailCodeDefault());

        // Revoking routes by kind to the existing audited use cases the /unban /unmute /unjail commands take.
        PunishmentRevoker revoker = (actor, target, kind) -> {
            switch (kind) {
                case BAN -> services.unban().unban(actor, target);
                case MUTE -> services.unmute().unmute(actor, target);
                case JAIL -> services.unjail().unjail(actor, target);
            }
        };

        // Break the list↔detail cycle with a one-slot holder: the detail view's back callback reopens the engine list
        // at click time, by which point it is constructed. This mirrors the worlds list→bespoke-editor wiring.
        ModerationActiveMenu[] listHolder = new ModerationActiveMenu[1];
        PunishmentDetailView detail = new PunishmentDetailView(
                menus,
                guiText,
                scheduler,
                revoker,
                clock,
                detailLayout,
                (player, viewer) -> listHolder[0].open(viewer),
                (player, punishment) -> historyMenu.open(
                        com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs.toRef(player),
                        punishment.target()));

        ModerationActiveMenu list =
                new ModerationActiveMenu(menus, scheduler, repository, players, messages, clock, detail);
        list.register(menuBindings, dataFolder, log);
        listHolder[0] = list;
        return new ModerationGuiViews(list, historyMenu);
    }

    /** Open the active-punishments list, the management GUI's entry point, for {@code viewer}. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        list.open(viewer);
    }

    /**
     * Open {@code target}'s read-only history for {@code viewer}, reusing the same {@link ModerationHistoryMenu} the
     * detail screen's history button drills into so the bare {@code /banhistory} picker and the in-GUI navigation
     * land on one view rather than a divergent second one.
     */
    public void openHistory(Player player, PlayerRef viewer, PlayerRef target) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        historyMenu.open(viewer, target.uuid());
    }

    private static EntityEditorLayout detailCodeDefault() {
        // Six properties (target, type, issuer, reason, remaining, history) across the upper rows, a back button,
        // and a revoke button wired to the editor's delete slot: the framework confirm-gates the revoke.
        return new EntityEditorLayout(
                3,
                java.util.List.of(10, 11, 12, 13, 14, 16),
                22,
                java.util.OptionalInt.of(26),
                Material.ARROW,
                Material.LAVA_BUCKET,
                Material.BLACK_STAINED_GLASS_PANE);
    }
}
