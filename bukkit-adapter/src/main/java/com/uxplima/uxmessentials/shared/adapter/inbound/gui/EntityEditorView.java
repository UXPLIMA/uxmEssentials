package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.EditorSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A reusable, config-driven property grid for editing one entity of type {@code T}: a chest laid out from an
 * {@link EntityEditorLayout}, with one button per {@link EditableProperty} drawn at its configured slot
 * (label as the name, current value as the lore), a back button, and an optional delete button gated behind a
 * confirm. The properties for an entity are supplied by the caller; a click on a property slot routes to that
 * property's {@link EditableProperty#onClick} with a {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui
 * .property.ClickContext} whose reopen hook redraws this editor so a value change shows.
 *
 * <p>The view is a thin shim over the menu engine's property-editor runtime: it turns the {@code (layout, title,
 * property list, value-lore, back, optional delete)} a module hands it into an {@link EditorSpec} and opens it
 * through {@link Menus#openEditor}, so the window is a holder-backed engine editor routed and torn down by the one
 * menu listener and one {@code closeMenu}. The geometry, materials and catalog keys are unchanged, so the rendered
 * editor is identical slot-for-slot to the bespoke view it replaces. The enum/list/colour property children become
 * engine child menus automatically (the engine's editor {@code ClickContext} carries the selector/confirm openers)
 * and the optional delete button gates through {@link Menus#confirm} rather than a uxmLib {@code ConfirmMenu}.
 *
 * <p>The view holds no module logic: the property list, the title, and the back/delete callbacks are all supplied by
 * the caller, and a property mutates through the module's own use case. {@link #open} hands the editor to the engine,
 * which resolves the live player and shows the inventory on the viewer's entity thread. The same
 * {@link EntityEditorLayout#propertySlots} drives both the render and {@link #propertyAt}, so a clicked slot always
 * resolves to the property drawn there.
 *
 * @param <T> the edited entity type
 */
@NullMarked
public final class EntityEditorView<T> {

    private final Menus menus;
    private final GuiText guiText;
    private final EntityEditorLayout layout;
    private final @Nullable MessageKey deleteConfirmTitle;
    private final Function<T, List<EditableProperty>> properties;
    private final @Nullable BiConsumer<Player, T> onDelete;
    private final EditorSpec spec;

    private EntityEditorView(Builder<T> builder) {
        this.menus = Objects.requireNonNull(builder.menus, "menus");
        this.guiText = Objects.requireNonNull(builder.guiText, "guiText");
        this.layout = Objects.requireNonNull(builder.layout, "layout");
        this.properties = Objects.requireNonNull(builder.properties, "properties");
        BiFunction<PlayerRef, T, Component> title = Objects.requireNonNull(builder.title, "title");
        MessageKey valueLore = Objects.requireNonNull(builder.valueLore, "valueLore");
        MessageKey backName = Objects.requireNonNull(builder.backName, "backName");
        BiConsumer<Player, PlayerRef> onBack = Objects.requireNonNull(builder.onBack, "onBack");
        Objects.requireNonNull(builder.scheduler, "scheduler");
        this.deleteConfirmTitle = builder.deleteConfirmTitle;
        this.onDelete = builder.onDelete;
        EditorSpec.Builder specBuilder = EditorSpec.builder()
                .layout(layout)
                // The engine spec is type-erased on the subject, so the typed title/property provider close over T
                // and cast the Object subject back. The subject is always the entity this view was opened with, so
                // a null here would be a wiring error and is rejected rather than rendered.
                .title((viewer, subject) -> title.apply(viewer, require(subject)))
                .valueLore(valueLore)
                .backName(backName)
                .properties(subject -> properties.apply(require(subject)))
                .onBack(onBack);
        if (builder.deleteName != null && builder.deleteConfirmTitle != null && builder.onDelete != null) {
            // The delete button does not delete on click: it opens the engine confirm, whose yes runs the delete and
            // whose no reopens this editor: the ConfirmMenu replacement, kept self-contained in the shim.
            specBuilder.onDelete(
                    builder.deleteName,
                    builder.deleteConfirmTitle,
                    (player, subject) -> confirmDelete(player, require(subject)));
        }
        this.spec = specBuilder.build();
    }

    /** Start building an editor view; required fields are validated at {@link Builder#build}. */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /** Open the editor for {@code entity} as an engine editor; the engine resolves the live player and entity-hops. */
    public void open(Player player, PlayerRef viewer, T entity) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(entity, "entity");
        menus.openEditor(viewer, spec, entity);
    }

    /**
     * The property drawn at {@code slot} for {@code entity}, or empty when the slot carries no property. The editor
     * uses the same mapping internally to turn a click into the property to edit; it is public so a module's tests
     * (or a bespoke listener) can assert the slot↔property mapping without firing a click. The i-th property goes in
     * the i-th of the layout's property slots, matching the engine renderer.
     */
    public Optional<EditableProperty> propertyAt(int slot, T entity) {
        Objects.requireNonNull(entity, "entity");
        List<EditableProperty> props = properties.apply(entity);
        int index = layout.propertySlots().indexOf(slot);
        return index >= 0 && index < props.size() ? Optional.of(props.get(index)) : Optional.empty();
    }

    /**
     * Open the engine confirm for a delete: yes runs the caller's delete handler, no reopens this editor. The delete
     * runs on the viewer's entity thread (the confirm's decision hop), exactly where the bespoke {@code ConfirmMenu}
     * ran it.
     */
    private void confirmDelete(Player player, T entity) {
        BiConsumer<Player, T> delete = Objects.requireNonNull(onDelete, "onDelete");
        MessageKey confirmTitle = Objects.requireNonNull(deleteConfirmTitle, "deleteConfirmTitle");
        PlayerRef viewer = BukkitRefs.toRef(player);
        Component title = guiText.text(viewer, confirmTitle);
        menus.confirm(viewer, title, () -> delete.accept(player, entity), () -> menus.openEditor(viewer, spec, entity));
    }

    /** Cast the engine's type-erased subject back to {@code T}; the subject is always the opened entity, never null. */
    @SuppressWarnings("unchecked") // the engine carries the very entity this typed view opened with
    private T require(@Nullable Object subject) {
        return (T) Objects.requireNonNull(subject, "subject");
    }

    /** Fluent builder so a module names only the parts it uses; delete is optional. */
    @NullMarked
    public static final class Builder<T> {
        private @Nullable Menus menus;
        private @Nullable GuiText guiText;
        private @Nullable Scheduler scheduler;
        private @Nullable EntityEditorLayout layout;
        private @Nullable BiFunction<PlayerRef, T, Component> title;
        private @Nullable MessageKey valueLore;
        private @Nullable MessageKey backName;
        private @Nullable MessageKey deleteName;
        private @Nullable MessageKey deleteConfirmTitle;
        private @Nullable Function<T, List<EditableProperty>> properties;
        private @Nullable BiConsumer<Player, PlayerRef> onBack;
        private @Nullable BiConsumer<Player, T> onDelete;

        private Builder() {}

        /** The menu engine the editor opens through; the editor it builds is a holder-backed engine menu. */
        public Builder<T> menus(Menus menus) {
            this.menus = Objects.requireNonNull(menus, "menus");
            return this;
        }

        public Builder<T> guiText(GuiText guiText) {
            this.guiText = Objects.requireNonNull(guiText, "guiText");
            return this;
        }

        public Builder<T> scheduler(Scheduler scheduler) {
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
            return this;
        }

        public Builder<T> layout(EntityEditorLayout layout) {
            this.layout = Objects.requireNonNull(layout, "layout");
            return this;
        }

        /** The editor title, resolved per viewer and entity (a module wraps the entity name in {@code <value>}). */
        public Builder<T> title(BiFunction<PlayerRef, T, Component> title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        /** The catalog line each property's current value is rendered into (carries a {@code {value}} placeholder). */
        public Builder<T> valueLore(MessageKey valueLore) {
            this.valueLore = Objects.requireNonNull(valueLore, "valueLore");
            return this;
        }

        public Builder<T> backName(MessageKey backName) {
            this.backName = Objects.requireNonNull(backName, "backName");
            return this;
        }

        public Builder<T> properties(Function<T, List<EditableProperty>> properties) {
            this.properties = Objects.requireNonNull(properties, "properties");
            return this;
        }

        public Builder<T> onBack(BiConsumer<Player, PlayerRef> onBack) {
            this.onBack = Objects.requireNonNull(onBack, "onBack");
            return this;
        }

        /** Wire the optional delete button: its name, the confirm title, and the delete handler. */
        public Builder<T> onDelete(
                MessageKey deleteName, MessageKey deleteConfirmTitle, BiConsumer<Player, T> onDelete) {
            this.deleteName = Objects.requireNonNull(deleteName, "deleteName");
            this.deleteConfirmTitle = Objects.requireNonNull(deleteConfirmTitle, "deleteConfirmTitle");
            this.onDelete = Objects.requireNonNull(onDelete, "onDelete");
            return this;
        }

        /** Build the view; the constructor validates that every required field was set. */
        public EntityEditorView<T> build() {
            return new EntityEditorView<>(this);
        }
    }
}
