package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.EntityListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A reusable, config-driven paginated management list over an entity type {@code T}. It draws one icon per
 * entity into the configured content slots (paging when there are more entities than fit), a previous/next
 * button at the layout's nav slots, an optional create button, and a glass-filler background, all geometry,
 * materials, and slots from an {@link EntityListLayout}, every visible string from a {@link MessageKey}. A
 * click on an entity icon invokes {@code onSelect} (a module wires that to open its {@link EntityEditorView});
 * a click on the create button invokes the optional {@code onCreate}.
 *
 * <p>The view is a thin shim over the menu engine's paginated-list runtime: it turns the {@code (layout, title,
 * entities, icon renderer, select, optional create/action)} a module hands it into an {@link EntityListSpec} and opens it
 * through {@link Menus#openList}, so the window is a holder-backed engine list routed and torn down by the one menu
 * listener and one {@code closeMenu}. The geometry, materials and catalog keys are unchanged, so the rendered list
 * is identical slot-for-slot to the bespoke {@code PaginatedGui} it replaces, and page flips re-paginate the same
 * holder rather than rebuilding a window.
 *
 * <p>The view holds no module logic: the entity supplier, the per-entity icon renderer, and the callbacks are
 * all supplied by the caller. {@link #open} hands the list to the engine, which resolves the live player and shows
 * the inventory on the viewer's entity thread; the caller pre-resolves the entity supplier off-thread (an
 * {@link java.util.concurrent.atomic.AtomicReference} snapshot), so the imperative icon renderer reads only that and
 * never touches Bukkit on a background thread.
 *
 * <p>A caller may also wire one optional <em>action button</em>: a non-entity control such as a settings opener,
 * through {@code onAction}. It is drawn over the filler at the slot and with the material the layout names, and its
 * click runs the supplied handler; the list stays the same paginated entity browser otherwise.
 *
 * @param <T> the managed entity type
 */
@NullMarked
public final class EntityListView<T> {

    private final Menus menus;
    private final GuiText guiText;
    private final EntityListLayout layout;
    private final MessageKey title;
    private final @Nullable MessageKey emptyTitle;
    private final MessageKey prevName;
    private final MessageKey nextName;
    private final @Nullable MessageKey createName;
    private final Supplier<List<T>> entities;
    private final BiFunction<PlayerRef, T, ItemStack> iconRenderer;
    private final BiConsumer<Player, T> onSelect;
    private final @Nullable Consumer<Player> onCreate;
    private final @Nullable MessageKey actionName;
    private final @Nullable Consumer<Player> onAction;

    private EntityListView(Builder<T> builder) {
        this.menus = Objects.requireNonNull(builder.menus, "menus");
        this.guiText = Objects.requireNonNull(builder.guiText, "guiText");
        Objects.requireNonNull(builder.scheduler, "scheduler");
        this.layout = Objects.requireNonNull(builder.layout, "layout");
        this.title = Objects.requireNonNull(builder.title, "title");
        this.emptyTitle = builder.emptyTitle;
        this.prevName = Objects.requireNonNull(builder.prevName, "prevName");
        this.nextName = Objects.requireNonNull(builder.nextName, "nextName");
        this.createName = builder.createName;
        this.entities = Objects.requireNonNull(builder.entities, "entities");
        this.iconRenderer = Objects.requireNonNull(builder.iconRenderer, "iconRenderer");
        this.onSelect = Objects.requireNonNull(builder.onSelect, "onSelect");
        this.onCreate = builder.onCreate;
        this.actionName = builder.actionName;
        this.onAction = builder.onAction;
    }

    /** Start building a list view; the required fields are validated at {@link Builder#build}. */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /** Open the list for {@code viewer} on its first page; the engine resolves the live player and entity-hops. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        menus.openList(viewer, spec(viewer));
    }

    /**
     * Build the engine {@link EntityListSpec} for one viewer. The title and button names resolve against {@code viewer},
     * so the spec is built per open (matching the bespoke view, which rebuilt its {@code PaginatedGui} per open); the
     * typed icon renderer and select handler close over {@code T} and cast the engine's type-erased {@link Object}
     * entity back, which is always one of the very entities this view's supplier produced.
     *
     * <p>When the snapshot is empty and the caller wired an {@code emptyTitle}, the window opens under that title
     * instead. The engine then draws just the filler and nav, an empty-state panel rather than a head grid, so a
     * list with nothing to show no longer needs a bespoke empty-state window.
     */
    private EntityListSpec spec(PlayerRef viewer) {
        List<T> snapshot = entities.get();
        MessageKey resolvedTitle = snapshot.isEmpty() && emptyTitle != null ? emptyTitle : title;
        EntityListSpec.Builder spec = EntityListSpec.builder()
                .title(guiText.text(viewer, resolvedTitle))
                .rows(layout.rows())
                .contentSlots(contentSlots())
                .navigation(
                        layout.base().prevSlot(),
                        layout.base().nextSlot(),
                        layout.base().navIcon())
                .navNames(guiText.text(viewer, prevName), guiText.text(viewer, nextName))
                .filler(layout.filler())
                .entities(() -> List.<Object>copyOf(entities.get()))
                .iconRenderer((v, entity) -> iconRenderer.apply(v, cast(entity)))
                .onSelect((player, entity) -> onSelect.accept(player, cast(entity)));
        if (onCreate != null && createName != null && layout.createSlot().isPresent()) {
            spec.onCreate(
                    layout.createSlot().getAsInt(), layout.createIcon(), guiText.text(viewer, createName), onCreate);
        }
        if (onAction != null && actionName != null && layout.actionSlot().isPresent()) {
            spec.onAction(
                    layout.actionSlot().getAsInt(), layout.actionIcon(), guiText.text(viewer, actionName), onAction);
        }
        return spec.build();
    }

    /** Cast the engine's type-erased entity back to {@code T}; it is always one of this view's own entities. */
    @SuppressWarnings("unchecked") // the engine carries the very entities this typed view's supplier produced
    private T cast(Object entity) {
        return (T) entity;
    }

    /**
     * The content slots an entity icon may occupy: the conf's explicit slots if it set any, else the default page
     * region (every slot of all but the bottom row), matching the bespoke view's {@code PaginatedGui} default.
     */
    private List<Integer> contentSlots() {
        return layout.explicitContentSlots().orElseGet(() -> {
            List<Integer> defaults = new ArrayList<>();
            int limit = (layout.rows() - 1) * 9;
            for (int slot = 0; slot < limit; slot++) {
                defaults.add(slot);
            }
            return defaults;
        });
    }

    /** Fluent builder so a module names only the parts it uses; create is optional. */
    @NullMarked
    public static final class Builder<T> {
        private @Nullable Menus menus;
        private @Nullable GuiText guiText;
        private @Nullable Scheduler scheduler;
        private @Nullable EntityListLayout layout;
        private @Nullable MessageKey title;
        private @Nullable MessageKey emptyTitle;
        private @Nullable MessageKey prevName;
        private @Nullable MessageKey nextName;
        private @Nullable MessageKey createName;
        private @Nullable Supplier<List<T>> entities;
        private @Nullable BiFunction<PlayerRef, T, ItemStack> iconRenderer;
        private @Nullable BiConsumer<Player, T> onSelect;
        private @Nullable Consumer<Player> onCreate;
        private @Nullable MessageKey actionName;
        private @Nullable Consumer<Player> onAction;

        private Builder() {}

        /** The menu engine the list opens through; the list it builds is a holder-backed engine menu. */
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

        public Builder<T> layout(EntityListLayout layout) {
            this.layout = Objects.requireNonNull(layout, "layout");
            return this;
        }

        public Builder<T> title(MessageKey title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        /**
         * The title shown when the entity snapshot is empty, an empty-state panel under this title rather than a
         * head/icon grid. Optional: a list that omits it keeps the regular {@link #title} even when empty.
         */
        public Builder<T> emptyTitle(MessageKey emptyTitle) {
            this.emptyTitle = Objects.requireNonNull(emptyTitle, "emptyTitle");
            return this;
        }

        /** The previous- and next-page button names. */
        public Builder<T> navNames(MessageKey prevName, MessageKey nextName) {
            this.prevName = Objects.requireNonNull(prevName, "prevName");
            this.nextName = Objects.requireNonNull(nextName, "nextName");
            return this;
        }

        public Builder<T> entities(Supplier<List<T>> entities) {
            this.entities = Objects.requireNonNull(entities, "entities");
            return this;
        }

        public Builder<T> iconRenderer(BiFunction<PlayerRef, T, ItemStack> iconRenderer) {
            this.iconRenderer = Objects.requireNonNull(iconRenderer, "iconRenderer");
            return this;
        }

        public Builder<T> onSelect(BiConsumer<Player, T> onSelect) {
            this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
            return this;
        }

        /** Wire the optional create button: its name and the click handler. The layout supplies the slot/icon. */
        public Builder<T> onCreate(MessageKey createName, Consumer<Player> onCreate) {
            this.createName = Objects.requireNonNull(createName, "createName");
            this.onCreate = Objects.requireNonNull(onCreate, "onCreate");
            return this;
        }

        /**
         * Wire the optional action button: a non-entity control (e.g. a settings opener) drawn over the filler under
         * {@code name}, whose click runs {@code onAction}. The layout supplies its slot and material, so a list whose
         * layout names no {@code action-slot} shows no such button.
         */
        public Builder<T> onAction(MessageKey name, Consumer<Player> onAction) {
            this.actionName = Objects.requireNonNull(name, "name");
            this.onAction = Objects.requireNonNull(onAction, "onAction");
            return this;
        }

        /** Build the view; the constructor validates that every required field was set. */
        public EntityListView<T> build() {
            return new EntityListView<>(this);
        }
    }
}
