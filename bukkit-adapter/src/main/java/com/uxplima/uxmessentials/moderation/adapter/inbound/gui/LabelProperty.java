package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.util.Objects;
import java.util.function.Function;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * A read-only detail line in a property grid: a labelled button whose lore shows a value the viewer cannot
 * change, and whose click does nothing. The active-punishment detail view is read-only for everything except
 * the explicit revoke and history buttons. The target, type, issuer, reason, and remaining time are facts an
 * admin inspects, not fields they edit, so each is a {@code LabelProperty}. It carries no use case and never
 * mutates: it is the {@link EditableProperty} the framework draws for a value that is shown but not edited. The
 * value resolver receives the viewer so a localised value (a kind name, "Permanent") renders in their locale.
 */
@NullMarked
public final class LabelProperty implements EditableProperty {

    private final MessageKey label;
    private final Material icon;
    private final Function<PlayerRef, String> value;

    public LabelProperty(MessageKey label, Material icon, Function<PlayerRef, String> value) {
        this.label = Objects.requireNonNull(label, "label");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.value = Objects.requireNonNull(value, "value");
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
        return value.apply(viewer);
    }

    @Override
    public void onClick(ClickContext context) {
        Objects.requireNonNull(context, "context");
        // A detail line is inspect-only; a click neither mutates nor navigates.
    }
}
