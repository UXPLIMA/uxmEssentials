package com.uxplima.uxmessentials.teleport.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.SettingsPanelView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ToggleProperty;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFlags;
import org.jspecify.annotations.NullMarked;

/**
 * The per-player teleport settings panel ({@code /tpsettings}, and the teleport entry on the {@code /uxmess gui}
 * hub): two toggles a player flips for themselves, each reading and writing the same {@link TeleportFlags} the
 * teleport commands do. The first mirrors {@code /tptoggle} (whether the player accepts incoming requests at
 * all); the second mirrors {@code /tpauto} (whether requests are auto-accepted). The panel holds no logic of its
 * own. It reads the flags fresh on every open and routes a flip through the existing port, so opening it always
 * shows the live state and a click is the same mutation the command makes.
 */
@NullMarked
public final class TeleportSettingsView {

    private static final String MODULE = "teleport";
    private static final String LAYOUT = "teleport-settings";

    private final SettingsPanelView panel;

    public TeleportSettingsView(
            GuiText guiText,
            Scheduler scheduler,
            GuiLayouts guiLayouts,
            Messages messages,
            TeleportFlags flags,
            Menus menus) {
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(flags, "flags");
        Objects.requireNonNull(menus, "menus");
        EntityEditorLayout layout =
                guiLayouts.loadEntityEditor(MODULE, LAYOUT, EntityEditorLayout.codeDefault(List.of(11, 15), 22));
        this.panel = SettingsPanelView.builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .menus(menus)
                .layout(layout)
                .title(TeleportMessageKey.GUI_SETTINGS_TITLE)
                .valueLore(TeleportMessageKey.GUI_SETTINGS_VALUE_LORE)
                .backName(TeleportMessageKey.GUI_SETTINGS_BACK)
                .settings(viewer -> settings(messages, scheduler, flags, viewer))
                .onBack((player, viewer) -> player.closeInventory())
                .build();
    }

    /** Open the panel for {@code player}, on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer) {
        panel.open(player, viewer);
    }

    private static List<EditableProperty> settings(
            Messages messages, Scheduler scheduler, TeleportFlags flags, PlayerRef viewer) {
        return List.of(
                ToggleProperty.ofBoolean(
                        TeleportMessageKey.GUI_SETTINGS_ACCEPT,
                        Material.ENDER_PEARL,
                        () -> flags.acceptsRequests(viewer),
                        (who, on) -> onOff(messages, who, on),
                        on -> flags.setAcceptsRequests(viewer, on),
                        scheduler),
                ToggleProperty.ofBoolean(
                        TeleportMessageKey.GUI_SETTINGS_AUTO_ACCEPT,
                        Material.LIME_DYE,
                        () -> flags.autoAccepts(viewer),
                        (who, on) -> onOff(messages, who, on),
                        on -> {
                            if (flags.autoAccepts(viewer) != on) {
                                flags.toggleAutoAccepts(viewer);
                            }
                        },
                        scheduler));
    }

    private static String onOff(Messages messages, PlayerRef viewer, boolean on) {
        return messages.resolve(
                viewer,
                on ? TeleportMessageKey.GUI_SETTINGS_VALUE_ON : TeleportMessageKey.GUI_SETTINGS_VALUE_OFF,
                Map.of());
    }
}
