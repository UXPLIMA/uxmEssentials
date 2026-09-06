package com.uxplima.uxmessentials.presence.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.presence.adapter.PresenceServices;
import com.uxplima.uxmessentials.presence.application.PresenceMessageKey;
import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
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
import org.jspecify.annotations.NullMarked;

/**
 * The per-player presence settings panel ({@code /presencesettings}, and the presence entry on the
 * {@code /uxmess gui} hub): two toggles a player flips for themselves. The first mirrors {@code /afk} (the player's
 * AFK state), writing through presence's own {@code MarkAfk}; the second mirrors {@code /vanish} (the player's vanish
 * state), reading the live {@link PresenceStore} (whose vanished bit is overlaid from the one vanish authority) and
 * writing through the injected {@code vanishToggle} handle onto the {@code vanish} context. When the vanish module is
 * disabled the handle is a no-op and the overlaid read stays {@code false}, so the toggle degrades cleanly. The panel
 * holds no logic of its own. It reads the current presence fresh on every open and routes a flip through the matching
 * handle, so opening it always shows the live state and a click is the same transition the command makes.
 */
@NullMarked
public final class PresenceSettingsView {

    private static final String MODULE = "presence";
    private static final String LAYOUT = "presence-settings";

    private final SettingsPanelView panel;

    public PresenceSettingsView(
            GuiText guiText,
            Scheduler scheduler,
            GuiLayouts guiLayouts,
            Messages messages,
            PresenceServices services,
            PresenceStore store,
            Consumer<PlayerRef> vanishToggle,
            Menus menus) {
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(vanishToggle, "vanishToggle");
        Objects.requireNonNull(menus, "menus");
        EntityEditorLayout layout =
                guiLayouts.loadEntityEditor(MODULE, LAYOUT, EntityEditorLayout.codeDefault(List.of(11, 15), 22));
        this.panel = SettingsPanelView.builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .menus(menus)
                .layout(layout)
                .title(PresenceMessageKey.GUI_SETTINGS_TITLE)
                .valueLore(PresenceMessageKey.GUI_SETTINGS_VALUE_LORE)
                .backName(PresenceMessageKey.GUI_SETTINGS_BACK)
                .settings(viewer -> settings(messages, scheduler, services, store, vanishToggle, viewer))
                .onBack((player, viewer) -> player.closeInventory())
                .build();
    }

    /** Open the panel for {@code player}, on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer) {
        panel.open(player, viewer);
    }

    private static List<EditableProperty> settings(
            Messages messages,
            Scheduler scheduler,
            PresenceServices services,
            PresenceStore store,
            Consumer<PlayerRef> vanishToggle,
            PlayerRef viewer) {
        return List.of(
                ToggleProperty.ofBoolean(
                        PresenceMessageKey.GUI_SETTINGS_AFK,
                        Material.CLOCK,
                        () -> store.current(viewer).afk(),
                        (who, on) -> onOff(messages, who, on),
                        on -> {
                            if (store.current(viewer).afk() != on) {
                                services.markAfk().toggle(viewer, Optional.empty());
                            }
                        },
                        scheduler),
                ToggleProperty.ofBoolean(
                        PresenceMessageKey.GUI_SETTINGS_VANISH,
                        Material.POTION,
                        () -> store.current(viewer).vanished(),
                        (who, on) -> onOff(messages, who, on),
                        on -> {
                            if (store.current(viewer).vanished() != on) {
                                vanishToggle.accept(viewer);
                            }
                        },
                        scheduler));
    }

    private static String onOff(Messages messages, PlayerRef viewer, boolean on) {
        return messages.resolve(
                viewer,
                on ? PresenceMessageKey.GUI_SETTINGS_VALUE_ON : PresenceMessageKey.GUI_SETTINGS_VALUE_OFF,
                Map.of());
    }
}
