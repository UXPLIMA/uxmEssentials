package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
import com.uxplima.uxmessentials.playerstate.application.ShowPlaytime;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.SettingsPanelView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code /playtime} GUI: a read-only panel of the same today / last-7-days / last-30-days / all-time
 * (active vs AFK) breakdown the chat command renders, plus the lifetime continuity line. A bare {@code /playtime}
 * opens the viewer's own panel; {@code /playtime <name>} opens that player's panel for a staff member holding the
 * {@code .others} node. The {@code gui-on} catalog flag binds this opener to the bare root; with {@code gui-off}
 * the command falls back to the chat {@code ShowPlaytime}.
 *
 * <p>The figures are read off the tick thread through the {@link Scheduler} port (the breakdown query hits the
 * DB-backed ledger), then the panel is opened on the viewer's entity thread with the already-rendered values
 * captured, so the entity-thread build does no I/O. The panel is a shared {@link SettingsPanelView} whose every
 * row is a read-only {@link PlaytimeRowProperty}: a labelled fact the viewer inspects but cannot change, matching
 * the moderation detail panels. Every visible string is a {@link PlayerstateMessageKey}, resolved per viewer.
 */
@NullMarked
public final class PlaytimeView {

    private static final String MODULE = "playerstate";
    private static final String PANEL_LAYOUT = "playtime-panel";

    // One row per breakdown window plus the lifetime line, in the panel's reading order. Each names its label key,
    // its icon, and the two placeholder keys (active / AFK) it pulls from the rendered breakdown map; the lifetime
    // row carries a single placeholder, so its AFK key is left null.
    private static final List<Row> ROWS = List.of(
            new Row(PlayerstateMessageKey.PLAYTIME_GUI_TODAY, Material.CLOCK, "today_active", "today_afk"),
            new Row(PlayerstateMessageKey.PLAYTIME_GUI_WEEK, Material.COMPASS, "week_active", "week_afk"),
            new Row(PlayerstateMessageKey.PLAYTIME_GUI_MONTH, Material.MAP, "month_active", "month_afk"),
            new Row(PlayerstateMessageKey.PLAYTIME_GUI_TOTAL, Material.BOOK, "total_active", "total_afk"),
            new Row(PlayerstateMessageKey.PLAYTIME_GUI_LIFETIME, Material.NETHER_STAR, "lifetime", null));

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final Messages messages;
    private final ShowPlaytime showPlaytime;
    private final Menus menus;
    private final EntityEditorLayout layout;

    public PlaytimeView(
            GuiText guiText,
            Scheduler scheduler,
            GuiLayouts guiLayouts,
            Messages messages,
            ShowPlaytime showPlaytime,
            Menus menus) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.showPlaytime = Objects.requireNonNull(showPlaytime, "showPlaytime");
        this.menus = Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        // A 3-row panel (slots 0..26): the five breakdown rows sit in the middle band, the close button last.
        this.layout = guiLayouts.loadEntityEditor(
                MODULE, PANEL_LAYOUT, EntityEditorLayout.codeDefault(List.of(10, 12, 14, 16, 22), 26));
    }

    /**
     * Open {@code subject}'s breakdown panel for {@code viewer}. The breakdown is read off-tick; the panel then
     * opens on the viewer's entity thread with the rendered figures captured, so no query runs on a region thread.
     */
    public void open(Player player, PlayerRef viewer, PlayerRef subject) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        scheduler.async(() -> {
            Map<String, String> breakdown = showPlaytime.breakdown(subject);
            panel(viewer, subject, breakdown).open(player, viewer);
        });
    }

    private SettingsPanelView panel(PlayerRef viewer, PlayerRef subject, Map<String, String> breakdown) {
        MessageKey title = viewer.equals(subject)
                ? PlayerstateMessageKey.PLAYTIME_GUI_TITLE
                : PlayerstateMessageKey.PLAYTIME_GUI_TITLE_OTHER;
        return SettingsPanelView.builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .menus(menus)
                .layout(layout)
                .title(title)
                .valueLore(PlayerstateMessageKey.PLAYTIME_GUI_VALUE_LORE)
                .backName(PlayerstateMessageKey.PLAYTIME_GUI_BACK)
                .settings(who -> rows(breakdown))
                .onBack((p, who) -> p.closeInventory())
                .build();
    }

    /** The read-only rows for the captured breakdown, in panel order. */
    private List<EditableProperty> rows(Map<String, String> breakdown) {
        return ROWS.stream()
                .map(row -> (EditableProperty) new PlaytimeRowProperty(messages, row, breakdown))
                .toList();
    }

    /** A breakdown window: its label, icon, and the placeholder key(s) it reads from the rendered map. */
    record Row(
            MessageKey label,
            Material icon,
            String activeKey,
            @org.jspecify.annotations.Nullable String afkKey) {
        Row {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(activeKey, "activeKey");
        }
    }
}
