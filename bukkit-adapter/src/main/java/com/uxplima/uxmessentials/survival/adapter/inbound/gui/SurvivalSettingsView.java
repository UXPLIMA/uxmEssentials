package com.uxplima.uxmessentials.survival.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.SettingsPanelView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ActionProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.survival.adapter.outbound.PdcSurvivalToggles;
import com.uxplima.uxmessentials.survival.application.SurvivalConfig;
import com.uxplima.uxmessentials.survival.application.SurvivalMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * The per-player panel opened by {@code /survival}: one row per survival mechanic that carries a personal toggle,
 * showing its current on/off state and flipping it on click. It lists only the mechanics the viewer can actually use
 *. A globally-disabled mechanic ({@code enabled = false} in the config) is hidden, and so is one the viewer lacks the
 * {@code .toggle} permission for, so the panel reflects exactly what that player is allowed to switch.
 *
 * <p>Each mechanic's toggle is a PDC stamp owned by the viewer's own region thread, so it is flipped from an
 * {@link ActionProperty} (whose click runs on that thread) rather than an async {@code ToggleProperty}: a click flips
 * the byte through the same {@link PdcSurvivalToggles} the {@code /treefeller}, {@code /veinminer}, … commands use, and
 * the value lore redraws to the new state, so the panel and the commands always agree. It rides the shared
 * {@link SettingsPanelView} over a {@code modules/survival/gui} layout and the message catalog, so its geometry is
 * operator-editable and none of its text is inlined.
 */
@NullMarked
public final class SurvivalSettingsView {

    private static final String MODULE = "survival";
    private static final String LAYOUT = "survival-settings";

    /** The property slots the seven mechanic rows fill, in the config order below. */
    private static final List<Integer> SLOTS = List.of(10, 11, 12, 13, 14, 15, 16);

    private final SettingsPanelView panel;
    private final Server server;
    private final Messages messages;
    private final Permissions permissions;
    private final List<Mechanic> mechanics;

    public SurvivalSettingsView(
            GuiText guiText,
            Scheduler scheduler,
            GuiLayouts guiLayouts,
            Messages messages,
            Permissions permissions,
            Menus menus,
            Server server,
            PdcSurvivalToggles toggles,
            SurvivalConfig config) {
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(menus, "menus");
        this.server = Objects.requireNonNull(server, "server");
        Objects.requireNonNull(toggles, "toggles");
        this.mechanics = mechanics(Objects.requireNonNull(config, "config"), toggles);
        EntityEditorLayout layout =
                guiLayouts.loadEntityEditor(MODULE, LAYOUT, EntityEditorLayout.codeDefault(SLOTS, 22));
        this.panel = SettingsPanelView.builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .menus(menus)
                .layout(layout)
                .title(SurvivalMessageKey.SURVIVAL_GUI_TITLE)
                .valueLore(SurvivalMessageKey.SURVIVAL_GUI_VALUE_LORE)
                .backName(SurvivalMessageKey.SURVIVAL_GUI_BACK)
                .settings(this::settings)
                .onBack((player, viewer) -> player.closeInventory())
                .build();
    }

    /** Open the panel for {@code player}, on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer) {
        panel.open(player, viewer);
    }

    /** The rows the viewer sees: each enabled mechanic they hold the toggle permission for, drawn as a flip button. */
    private List<EditableProperty> settings(PlayerRef viewer) {
        List<EditableProperty> properties = new ArrayList<>(mechanics.size());
        for (Mechanic mechanic : mechanics) {
            if (mechanic.enabled() && permissions.has(viewer, mechanic.togglePermission())) {
                properties.add(new ActionProperty(
                        mechanic.label(),
                        mechanic.icon(),
                        who -> onOff(who, activeFor(who, mechanic)),
                        (player, reopen) -> flip(mechanic, player, reopen)));
            }
        }
        return List.copyOf(properties);
    }

    /** Flip the mechanic on the viewer's own thread (the click thread), then redraw the new state. */
    private static void flip(Mechanic mechanic, Player player, Runnable reopen) {
        mechanic.flip().accept(player);
        reopen.run();
    }

    /** Read a mechanic's current active state for {@code who}; an offline viewer reads the default-on. */
    private boolean activeFor(PlayerRef who, Mechanic mechanic) {
        Player player = server.getPlayer(who.uuid());
        return player == null || mechanic.active().test(player);
    }

    private String onOff(PlayerRef viewer, boolean on) {
        return messages.resolve(
                viewer,
                on ? SurvivalMessageKey.SURVIVAL_GUI_VALUE_ON : SurvivalMessageKey.SURVIVAL_GUI_VALUE_OFF,
                Map.of());
    }

    /** The seven toggleable mechanics in display order, each bound to its config gate and its PDC read/flip. */
    private static List<Mechanic> mechanics(SurvivalConfig config, PdcSurvivalToggles toggles) {
        return List.of(
                new Mechanic(
                        SurvivalMessageKey.SURVIVAL_GUI_TREEFELLER,
                        Material.OAK_LOG,
                        "uxmessentials.survival.treefeller.toggle",
                        config.treeFeller().enabled(),
                        p -> toggles.treeFellerActive(p, true),
                        p -> toggles.toggleTreeFeller(p, true)),
                new Mechanic(
                        SurvivalMessageKey.SURVIVAL_GUI_VEINMINER,
                        Material.IRON_ORE,
                        "uxmessentials.survival.veinminer.toggle",
                        config.veinminer().enabled(),
                        p -> toggles.veinminerActive(p, true),
                        p -> toggles.toggleVeinminer(p, true)),
                new Mechanic(
                        SurvivalMessageKey.SURVIVAL_GUI_FARMPROTECT,
                        Material.FARMLAND,
                        "uxmessentials.survival.farmprotect.toggle",
                        config.farmProtect().enabled(),
                        p -> toggles.farmProtectActive(p, true),
                        p -> toggles.toggleFarmProtect(p, true)),
                new Mechanic(
                        SurvivalMessageKey.SURVIVAL_GUI_AUTOPICKUP,
                        Material.HOPPER,
                        "uxmessentials.survival.autopickup.toggle",
                        config.autoPickup().enabled(),
                        p -> toggles.autoPickupActive(p, true),
                        p -> toggles.toggleAutoPickup(p, true)),
                new Mechanic(
                        SurvivalMessageKey.SURVIVAL_GUI_AUTOSMELT,
                        Material.FURNACE,
                        "uxmessentials.survival.autosmelt.toggle",
                        config.autoSmelt().enabled(),
                        p -> toggles.autoSmeltActive(p, true),
                        p -> toggles.toggleAutoSmelt(p, true)),
                new Mechanic(
                        SurvivalMessageKey.SURVIVAL_GUI_AUTOSELL,
                        Material.GOLD_INGOT,
                        "uxmessentials.survival.autosell.toggle",
                        config.autoSell().enabled(),
                        p -> toggles.autoSellActive(p, false),
                        p -> toggles.toggleAutoSell(p, false)),
                new Mechanic(
                        SurvivalMessageKey.SURVIVAL_GUI_AUTOTOOL,
                        Material.DIAMOND_PICKAXE,
                        "uxmessentials.survival.autotool.toggle",
                        config.autoTool().enabled(),
                        p -> toggles.autoToolActive(p, true),
                        p -> toggles.toggleAutoTool(p, true)));
    }

    /**
     * One toggleable mechanic drawn as a row: its label, icon, {@code .toggle} permission, config enable gate, and the
     * PDC read/flip the row's status and click route through.
     */
    private record Mechanic(
            MessageKey label,
            Material icon,
            String togglePermission,
            boolean enabled,
            Predicate<Player> active,
            Consumer<Player> flip) {}
}
