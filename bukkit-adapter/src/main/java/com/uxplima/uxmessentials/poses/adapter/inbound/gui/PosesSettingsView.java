package com.uxplima.uxmessentials.poses.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.poses.application.PoseSessions;
import com.uxplima.uxmessentials.poses.application.PosesMessageKey;
import com.uxplima.uxmessentials.poses.application.TogglePlayerSit;
import com.uxplima.uxmessentials.poses.application.port.PlayerSitPreferences;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.SettingsPanelView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ActionProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The per-player poses panel opened by a bare {@code /poses} (and the poses entry on the {@code /uxmess gui} hub):
 * a personal settings/status window. It shows whether the viewer is currently posing (a live read of the
 * {@link PoseSessions} registry) and lets them flip the {@code /poses toggle} opt-out. Whether other players may
 * sit on them: without leaving the screen. Both buttons hold no logic of their own: the status reads the registry
 * fresh on every draw, and the opt-out routes a flip through the same {@link TogglePlayerSit} use case the
 * {@code /poses toggle} command uses, so the panel and the command always agree.
 *
 * <p>The opt-out is a {@link PlayerSitPreferences} PDC preference owned by the viewer's own region thread, so it is
 * flipped from an {@link ActionProperty} (whose click runs on that thread) rather than an async toggle, a click
 * flips the byte and redraws, and the value lore shows the new {@code allow}/{@code refuse} state. The panel mirrors
 * the presence settings panel: it rides the shared {@link SettingsPanelView} over a {@code modules/poses/gui} layout
 * and the message catalog, so its geometry is operator-editable and none of its text is inlined.
 */
@NullMarked
public final class PosesSettingsView {

    private static final String MODULE = "poses";
    private static final String LAYOUT = "poses-settings";

    private final SettingsPanelView panel;

    public PosesSettingsView(
            GuiText guiText,
            Scheduler scheduler,
            GuiLayouts guiLayouts,
            Messages messages,
            Menus menus,
            PoseSessions sessions,
            PlayerSitPreferences preferences,
            TogglePlayerSit togglePlayerSit) {
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(sessions, "sessions");
        Objects.requireNonNull(preferences, "preferences");
        Objects.requireNonNull(togglePlayerSit, "togglePlayerSit");
        EntityEditorLayout layout =
                guiLayouts.loadEntityEditor(MODULE, LAYOUT, EntityEditorLayout.codeDefault(List.of(11, 15), 22));
        this.panel = SettingsPanelView.builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .menus(menus)
                .layout(layout)
                .title(PosesMessageKey.POSES_GUI_TITLE)
                .valueLore(PosesMessageKey.POSES_GUI_VALUE_LORE)
                .backName(PosesMessageKey.POSES_GUI_BACK)
                .settings(viewer -> settings(messages, sessions, preferences, togglePlayerSit))
                .onBack((player, viewer) -> player.closeInventory())
                .build();
    }

    /** Open the panel for {@code player}, on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer) {
        panel.open(player, viewer);
    }

    private static List<EditableProperty> settings(
            Messages messages,
            PoseSessions sessions,
            PlayerSitPreferences preferences,
            TogglePlayerSit togglePlayerSit) {
        return List.of(
                new ActionProperty(
                        PosesMessageKey.POSES_GUI_STATUS,
                        Material.ARMOR_STAND,
                        who -> onOff(messages, who, sessions.isPosing(who)),
                        (player, reopen) -> reopen.run()),
                new ActionProperty(
                        PosesMessageKey.POSES_GUI_PLAYERSIT,
                        Material.SADDLE,
                        who -> onOff(messages, who, preferences.allowsSitting(who)),
                        (player, reopen) -> flipPlayerSit(togglePlayerSit, player, reopen)));
    }

    /** Flip the viewer's player-sit opt-out on their own thread (the click thread), then redraw the new state. */
    private static void flipPlayerSit(TogglePlayerSit togglePlayerSit, Player player, Runnable reopen) {
        togglePlayerSit.toggle(BukkitRefs.toRef(player));
        reopen.run();
    }

    private static String onOff(Messages messages, PlayerRef viewer, boolean on) {
        return messages.resolve(
                viewer, on ? PosesMessageKey.POSES_GUI_VALUE_ON : PosesMessageKey.POSES_GUI_VALUE_OFF, Map.of());
    }
}
