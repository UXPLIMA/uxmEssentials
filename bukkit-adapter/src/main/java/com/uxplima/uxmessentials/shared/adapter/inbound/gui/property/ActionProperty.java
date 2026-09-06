package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * A property whose click runs a one-shot action rather than cycling a value. The "do-it-now" button a settings
 * panel or launcher hub places next to its toggles. The motivating cases are a button that opens a confirm or an
 * anvil, a button that opens another menu, or a button that fires a fire-and-forget admin action. The handler is
 * invoked with the live {@link Player} who clicked and a {@code reopen} runnable so it can open a sub-view (or
 * re-render) safely; it carries no domain logic of its own: the module's own use case sits inside the handler.
 *
 * <p>Unlike {@link ToggleProperty} this property has no editable current value, so its value lore is a fixed hint
 * resolved from the catalog (never an inline literal). The hint may depend on the viewer (so a count or a state
 * line can be shown), so the caller supplies it as a {@link Function} of the viewer.
 */
@NullMarked
public final class ActionProperty implements EditableProperty {

    private final MessageKey label;
    private final Material icon;
    private final Function<PlayerRef, String> valueHint;
    private final BiConsumer<Player, Runnable> handler;

    public ActionProperty(
            MessageKey label,
            Material icon,
            Function<PlayerRef, String> valueHint,
            BiConsumer<Player, Runnable> handler) {
        this.label = Objects.requireNonNull(label, "label");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.valueHint = Objects.requireNonNull(valueHint, "valueHint");
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    /** A convenience for an action whose hint is the same fixed string for every viewer. */
    public static ActionProperty of(
            MessageKey label, Material icon, String valueHint, BiConsumer<Player, Runnable> handler) {
        Objects.requireNonNull(valueHint, "valueHint");
        return new ActionProperty(label, icon, viewer -> valueHint, handler);
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
        return valueHint.apply(viewer);
    }

    @Override
    public void onClick(ClickContext context) {
        Objects.requireNonNull(context, "context");
        handler.accept(context.player(), context.reopen());
    }
}
