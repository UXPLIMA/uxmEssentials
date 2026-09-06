package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * A property whose click runs a one-shot navigation action. The "do-it-now" button the value editors do not
 * cover. The motivating case here is the detail view's "view player history" button: it does not change a
 * value, it opens another menu. The handler is invoked on the viewer's entity thread with the live
 * {@link Player} and a {@code reopen} runnable, so it can open a sub-view (or re-render) safely. It carries no
 * domain logic; the value lore is a fixed catalog hint, since the button has no editable current value.
 */
@NullMarked
public final class ModerationActionProperty implements EditableProperty {

    private final MessageKey label;
    private final Material icon;
    private final String valueHint;
    private final BiConsumer<Player, Runnable> handler;

    public ModerationActionProperty(
            MessageKey label, Material icon, String valueHint, BiConsumer<Player, Runnable> handler) {
        this.label = Objects.requireNonNull(label, "label");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.valueHint = Objects.requireNonNull(valueHint, "valueHint");
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public MessageKey label() {
        return label;
    }

    @Override
    public Material icon() {
        return icon;
    }

    @Override
    public String valueLore(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return valueHint;
    }

    @Override
    public void onClick(ClickContext context) {
        Objects.requireNonNull(context, "context");
        handler.accept(context.player(), context.reopen());
    }
}
