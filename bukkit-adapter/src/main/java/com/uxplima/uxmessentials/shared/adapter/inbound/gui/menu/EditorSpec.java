package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;

/**
 * The editor analog of a {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec}: the
 * recipe the engine draws a typed property editor from. It re-homes exactly the fields the bespoke
 * {@code EntityEditorView.Builder} already collects. The {@link EntityEditorLayout} (rows, the ordered property
 * slots, the back/delete slots and their materials), a per-viewer title, the value-lore catalog line each
 * property's current value renders into, the back label, an optional delete label plus confirm title, and the
 * function that derives the live property list from the subject, and adds the back/optional-delete callbacks.
 *
 * <p>This is the engine's public editor surface, opened through {@link Menus#openEditor}. Unlike the bespoke view
 * it is type-erased on the subject ({@code Object}): the engine routes clicks and re-renders without naming the
 * edited type, and a typed caller (or the later {@code EntityEditorView} shim) closes over its own {@code T} in the
 * property provider and the callbacks. The properties themselves are unchanged in shape; the spec only describes how
 * to lay them out and where back/delete sit.
 *
 * <p>Every label/value/title is a {@link MessageKey} or a {@link Component} the caller resolved from one, so an
 * editor opened through this spec carries no inline user-facing literal.
 */
public final class EditorSpec {

    private final EntityEditorLayout layout;
    private final BiFunction<PlayerRef, @Nullable Object, Component> title;
    private final MessageKey valueLore;
    private final MessageKey backName;
    private final @Nullable MessageKey deleteName;
    private final @Nullable MessageKey deleteConfirmTitle;
    private final Function<@Nullable Object, List<EditableProperty>> properties;
    private final BiConsumer<Player, PlayerRef> onBack;
    private final @Nullable BiConsumer<Player, @Nullable Object> onDelete;

    private EditorSpec(Builder builder) {
        this.layout = Objects.requireNonNull(builder.layout, "layout");
        this.title = Objects.requireNonNull(builder.title, "title");
        this.valueLore = Objects.requireNonNull(builder.valueLore, "valueLore");
        this.backName = Objects.requireNonNull(builder.backName, "backName");
        this.deleteName = builder.deleteName;
        this.deleteConfirmTitle = builder.deleteConfirmTitle;
        this.properties = Objects.requireNonNull(builder.properties, "properties");
        this.onBack = Objects.requireNonNull(builder.onBack, "onBack");
        this.onDelete = builder.onDelete;
    }

    /** Start building an editor spec; required fields are validated at {@link Builder#build}. */
    public static Builder builder() {
        return new Builder();
    }

    public EntityEditorLayout layout() {
        return layout;
    }

    public MessageKey valueLore() {
        return valueLore;
    }

    public MessageKey backName() {
        return backName;
    }

    public Optional<MessageKey> deleteName() {
        return Optional.ofNullable(deleteName);
    }

    public Optional<MessageKey> deleteConfirmTitle() {
        return Optional.ofNullable(deleteConfirmTitle);
    }

    public BiConsumer<Player, PlayerRef> onBack() {
        return onBack;
    }

    public Optional<BiConsumer<Player, @Nullable Object>> onDelete() {
        return Optional.ofNullable(onDelete);
    }

    /** Resolve the editor title for {@code viewer} editing {@code subject}, the same per-open title the view shows. */
    public Component title(PlayerRef viewer, @Nullable Object subject) {
        Objects.requireNonNull(viewer, "viewer");
        return title.apply(viewer, subject);
    }

    /** The live property list for {@code subject}, re-read on every draw exactly as the bespoke view re-reads it. */
    public List<EditableProperty> propertiesFor(@Nullable Object subject) {
        return properties.apply(subject);
    }

    /** Whether the optional delete button is fully configured (label, confirm title, handler, and a layout slot). */
    public boolean hasDelete() {
        return onDelete != null
                && deleteName != null
                && deleteConfirmTitle != null
                && layout.deleteSlot().isPresent();
    }

    /** Fluent builder mirroring the bespoke {@code EntityEditorView.Builder} so the later shim is mechanical. */
    public static final class Builder {
        private @Nullable EntityEditorLayout layout;
        private @Nullable BiFunction<PlayerRef, @Nullable Object, Component> title;
        private @Nullable MessageKey valueLore;
        private @Nullable MessageKey backName;
        private @Nullable MessageKey deleteName;
        private @Nullable MessageKey deleteConfirmTitle;
        private @Nullable Function<@Nullable Object, List<EditableProperty>> properties;
        private @Nullable BiConsumer<Player, PlayerRef> onBack;
        private @Nullable BiConsumer<Player, @Nullable Object> onDelete;

        private Builder() {}

        public Builder layout(EntityEditorLayout layout) {
            this.layout = Objects.requireNonNull(layout, "layout");
            return this;
        }

        /** The editor title, resolved per viewer and subject (a caller wraps the subject name in {@code <value>}). */
        public Builder title(BiFunction<PlayerRef, @Nullable Object, Component> title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        /** The catalog line each property's current value renders into (carries a {@code {value}} placeholder). */
        public Builder valueLore(MessageKey valueLore) {
            this.valueLore = Objects.requireNonNull(valueLore, "valueLore");
            return this;
        }

        public Builder backName(MessageKey backName) {
            this.backName = Objects.requireNonNull(backName, "backName");
            return this;
        }

        public Builder properties(Function<@Nullable Object, List<EditableProperty>> properties) {
            this.properties = Objects.requireNonNull(properties, "properties");
            return this;
        }

        public Builder onBack(BiConsumer<Player, PlayerRef> onBack) {
            this.onBack = Objects.requireNonNull(onBack, "onBack");
            return this;
        }

        /** Wire the optional delete button: its name, the confirm title, and the delete handler. */
        public Builder onDelete(
                MessageKey deleteName, MessageKey deleteConfirmTitle, BiConsumer<Player, @Nullable Object> onDelete) {
            this.deleteName = Objects.requireNonNull(deleteName, "deleteName");
            this.deleteConfirmTitle = Objects.requireNonNull(deleteConfirmTitle, "deleteConfirmTitle");
            this.onDelete = Objects.requireNonNull(onDelete, "onDelete");
            return this;
        }

        /** Build the spec; the constructor validates that every required field was set. */
        public EditorSpec build() {
            return new EditorSpec(this);
        }
    }
}
