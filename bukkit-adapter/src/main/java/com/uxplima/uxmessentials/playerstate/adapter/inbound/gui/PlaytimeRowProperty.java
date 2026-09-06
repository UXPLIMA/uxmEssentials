package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;

import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * One read-only row in the {@code /playtime} panel: a labelled window of the breakdown whose lore shows the
 * active (and, where the window has one, the AFK) figure the viewer inspects but cannot change. It is the
 * playtime panel's analogue of the moderation {@code LabelProperty}, a {@link EditableProperty} the framework
 * draws for a value shown but never edited, so {@link #onClick} does nothing.
 *
 * <p>The figures come pre-rendered in the captured breakdown map (the same compact {@code Nd Nh Nm} strings the
 * chat command interpolates), so the value resolver only formats the row line. The active/AFK split is rendered
 * through the {@code row-value} catalog line; the lifetime window, which carries no AFK figure, renders its single
 * value through the same line with the AFK placeholder blank.
 */
@NullMarked
final class PlaytimeRowProperty implements EditableProperty {

    private final Messages messages;
    private final PlaytimeView.Row row;
    private final Map<String, String> breakdown;

    PlaytimeRowProperty(Messages messages, PlaytimeView.Row row, Map<String, String> breakdown) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.row = Objects.requireNonNull(row, "row");
        this.breakdown = Map.copyOf(Objects.requireNonNull(breakdown, "breakdown"));
    }

    @Override
    public MessageKey label() {
        return row.label();
    }

    @Override
    public Material icon() {
        return row.icon();
    }

    @Override
    public String valueLore(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        String active = breakdown.getOrDefault(row.activeKey(), "");
        String afk = row.afkKey() == null ? "" : breakdown.getOrDefault(row.afkKey(), "");
        return messages.resolve(
                viewer,
                row.afkKey() == null
                        ? PlayerstateMessageKey.PLAYTIME_GUI_LIFETIME_VALUE
                        : PlayerstateMessageKey.PLAYTIME_GUI_ROW_VALUE,
                Map.of("active", active, "afk", afk));
    }

    @Override
    public void onClick(ClickContext context) {
        Objects.requireNonNull(context, "context");
        // A breakdown row is inspect-only; a click neither mutates nor navigates.
    }
}
