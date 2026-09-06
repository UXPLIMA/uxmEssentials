package com.uxplima.uxmessentials.scoreboard.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.scoreboard.application.ScoreboardMessageKey;
import com.uxplima.uxmessentials.scoreboard.application.ToggleScoreboard;
import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
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
 * The per-player scoreboard settings panel ({@code /scoreboard gui}, {@code /sb gui}, and the scoreboard entry on
 * the {@code /uxmess gui} hub): a {@link SettingsPanelView} with the single show/hide toggle the {@code /scoreboard}
 * command flips. The toggle reads the live {@link ScoreboardVisibilityStore} FRESH each open and writes through the
 * same {@link ToggleScoreboard} use case the command uses, flipping only when the desired state differs from the
 * stored one so a re-click on an already-correct state is a no-op.
 *
 * <p>Kept honest: which board a viewer sees is resolved automatically from each board's condition and priority
 * ({@code SidebarConfig.select}). There is no use case for a player to manually pick a board, so the panel exposes
 * no board-picker, only the genuine per-player preference. Every visible string is a {@link ScoreboardMessageKey};
 * the use-case write hops off the tick thread through the kernel {@link Scheduler}.
 */
@NullMarked
public final class ScoreboardSettingsView {

    private static final String MODULE = "scoreboard";
    private static final String PANEL_LAYOUT = "scoreboard-settings";
    private static final int VISIBILITY_SLOT = 13;

    private final SettingsPanelView panel;

    public ScoreboardSettingsView(
            GuiText guiText,
            Scheduler scheduler,
            GuiLayouts guiLayouts,
            Messages messages,
            ScoreboardVisibilityStore visibility,
            ToggleScoreboard toggle,
            Menus menus) {
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(toggle, "toggle");
        Objects.requireNonNull(menus, "menus");

        EntityEditorLayout layout = guiLayouts.loadEntityEditor(
                MODULE, PANEL_LAYOUT, EntityEditorLayout.codeDefault(List.of(VISIBILITY_SLOT), 22));
        this.panel = SettingsPanelView.builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .menus(menus)
                .layout(layout)
                .title(ScoreboardMessageKey.GUI_TITLE)
                .valueLore(ScoreboardMessageKey.GUI_VALUE_LORE)
                .backName(ScoreboardMessageKey.GUI_BACK)
                .settings(viewer -> buttons(scheduler, messages, visibility, toggle, viewer))
                .onBack((player, who) -> player.closeInventory())
                .build();
    }

    /** Open the panel for {@code player}, on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer) {
        panel.open(player, Objects.requireNonNull(viewer, "viewer"));
    }

    /** The settings panel, exposed package-private so the test can assert the toggle↔slot mapping. */
    SettingsPanelView panel() {
        return panel;
    }

    private List<EditableProperty> buttons(
            Scheduler scheduler,
            Messages messages,
            ScoreboardVisibilityStore visibility,
            ToggleScoreboard toggle,
            PlayerRef viewer) {
        // The toggle's value is "shown" (the inverse of the store's "hidden" bit) so the button reads naturally.
        ToggleProperty<Boolean> show = ToggleProperty.ofBoolean(
                ScoreboardMessageKey.GUI_VISIBILITY,
                Material.PAINTING,
                () -> !visibility.hidden(viewer),
                (who, shown) -> shownState(messages, who, shown),
                shown -> {
                    if (!visibility.hidden(viewer) != shown) {
                        toggle.toggle(viewer);
                    }
                },
                scheduler);
        return List.of(show);
    }

    private static String shownState(Messages messages, PlayerRef viewer, boolean shown) {
        return messages.resolve(
                viewer, shown ? ScoreboardMessageKey.GUI_VALUE_SHOWN : ScoreboardMessageKey.GUI_VALUE_HIDDEN, Map.of());
    }
}
