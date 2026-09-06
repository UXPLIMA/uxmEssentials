package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * An {@link EditableProperty} whose click opens a sub-editor built on the menu engine's child windows, the row the
 * item editor places for "Click actions" and "View requirements". Unlike {@code ActionProperty} it hands the full
 * {@link ClickContext} to its opener, so the sub-editor can reach the context's {@code opener} / {@code confirmOpener}
 * / {@code reopen} to draw its selector and confirm children on the same holder and teardown; a plain action handler
 * that only saw the player could not.
 *
 * <p>The value lore is a viewer-dependent hint (a count of the bound actions or requirements), resolved by the caller
 * as a {@link Function}, never an inline literal: it holds no domain logic and mutates nothing itself.
 */
@NullMarked
final class MenuOpenerProperty implements EditableProperty {

    private final MessageKey label;
    private final Material icon;
    private final Function<PlayerRef, String> valueHint;
    private final Consumer<ClickContext> onOpen;

    MenuOpenerProperty(
            MessageKey label, Material icon, Function<PlayerRef, String> valueHint, Consumer<ClickContext> onOpen) {
        this.label = Objects.requireNonNull(label, "label");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.valueHint = Objects.requireNonNull(valueHint, "valueHint");
        this.onOpen = Objects.requireNonNull(onOpen, "onOpen");
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
        onOpen.accept(context);
    }
}
