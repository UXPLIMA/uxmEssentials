package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The list analog of an {@link EditorSpec}: the recipe the engine draws a paginated entity list from. It re-homes
 * exactly the fields the bespoke {@code EntityListView} already collects, the per-viewer title, the geometry
 * (rows, the content slots, the previous/next nav slots and their icon, the background filler), the entity
 * supplier (the caller pre-resolves it off-thread, so the engine reads only the snapshot), the per-entity icon
 * renderer, the entity-click handler, and the optional create and action buttons.
 *
 * <p>This is the engine's public list surface, opened through {@link Menus#openList}. Like {@link EditorSpec} it is
 * type-erased on the entity ({@code Object}): the engine paginates, paints and routes clicks without naming the
 * listed type, and a typed caller (the {@code EntityListView} shim) closes over its own {@code T} in the supplier,
 * the icon renderer and the select handler. The icon renderer is imperative and runs on the viewer's entity thread
 * over the already-snapshotted entities, so it never touches Bukkit off-thread.
 *
 * <p>Every title and button name is a {@link Component} the caller resolved from a {@code MessageKey} (the icon
 * renderer bakes its own catalog text into each icon), so a list opened through this spec carries no inline
 * user-facing literal of its own.
 */
@NullMarked
public final class EntityListSpec {

    private final Component title;
    private final int rows;
    private final List<Integer> contentSlots;
    private final int prevSlot;
    private final int nextSlot;
    private final Material navIcon;
    private final Material filler;
    private final Component prevName;
    private final Component nextName;
    private final Supplier<List<Object>> entities;
    private final BiFunction<PlayerRef, Object, ItemStack> iconRenderer;
    private final BiConsumer<Player, Object> onSelect;
    private final OptionalInt createSlot;
    private final Material createIcon;
    private final @Nullable Component createName;
    private final @Nullable Consumer<Player> onCreate;
    private final OptionalInt actionSlot;
    private final Material actionIcon;
    private final @Nullable Component actionName;
    private final @Nullable Consumer<Player> onAction;
    private final List<ExtraButton> extraButtons;

    private EntityListSpec(Builder builder) {
        this.title = Objects.requireNonNull(builder.title, "title");
        this.rows = builder.rows;
        this.contentSlots = List.copyOf(Objects.requireNonNull(builder.contentSlots, "contentSlots"));
        this.prevSlot = builder.prevSlot;
        this.nextSlot = builder.nextSlot;
        this.navIcon = Objects.requireNonNull(builder.navIcon, "navIcon");
        this.filler = Objects.requireNonNull(builder.filler, "filler");
        this.prevName = Objects.requireNonNull(builder.prevName, "prevName");
        this.nextName = Objects.requireNonNull(builder.nextName, "nextName");
        this.entities = Objects.requireNonNull(builder.entities, "entities");
        this.iconRenderer = Objects.requireNonNull(builder.iconRenderer, "iconRenderer");
        this.onSelect = Objects.requireNonNull(builder.onSelect, "onSelect");
        this.createSlot = builder.createSlot;
        this.createIcon = Objects.requireNonNull(builder.createIcon, "createIcon");
        this.createName = builder.createName;
        this.onCreate = builder.onCreate;
        this.actionSlot = builder.actionSlot;
        this.actionIcon = Objects.requireNonNull(builder.actionIcon, "actionIcon");
        this.actionName = builder.actionName;
        this.onAction = builder.onAction;
        this.extraButtons = List.copyOf(Objects.requireNonNull(builder.extraButtons, "extraButtons"));
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6, was " + rows);
        }
    }

    /** Start building an entity-list spec; required fields are validated at {@link Builder#build}. */
    public static Builder builder() {
        return new Builder();
    }

    public Component title() {
        return title;
    }

    public int rows() {
        return rows;
    }

    public List<Integer> contentSlots() {
        return contentSlots;
    }

    public int prevSlot() {
        return prevSlot;
    }

    public int nextSlot() {
        return nextSlot;
    }

    public Material navIcon() {
        return navIcon;
    }

    public Material filler() {
        return filler;
    }

    public Component prevName() {
        return prevName;
    }

    public Component nextName() {
        return nextName;
    }

    /** The already-snapshotted entities to lay across the content slots, read once per draw on the entity thread. */
    public List<Object> entities() {
        return entities.get();
    }

    public BiFunction<PlayerRef, Object, ItemStack> iconRenderer() {
        return iconRenderer;
    }

    public BiConsumer<Player, Object> onSelect() {
        return onSelect;
    }

    /** The create button's slot if the list allows creation and the layout reserves one, else empty. */
    public OptionalInt createSlot() {
        return onCreate == null || createName == null ? OptionalInt.empty() : createSlot;
    }

    public Material createIcon() {
        return createIcon;
    }

    public Optional<Component> createName() {
        return Optional.ofNullable(createName);
    }

    public Optional<Consumer<Player>> onCreate() {
        return Optional.ofNullable(onCreate);
    }

    /** The action button's slot if the caller wired one, else empty. */
    public OptionalInt actionSlot() {
        return onAction == null || actionName == null ? OptionalInt.empty() : actionSlot;
    }

    public Material actionIcon() {
        return actionIcon;
    }

    public Optional<Component> actionName() {
        return Optional.ofNullable(actionName);
    }

    public Optional<Consumer<Player>> onAction() {
        return Optional.ofNullable(onAction);
    }

    /**
     * Any fixed, pre-built buttons the list draws alongside the entities and nav, beyond the single create and action
     * buttons, each at its own slot with its own click handler. The list renderer paints each icon as-is and records
     * its click, so a caller that needs more than the create/action pair (the shared player picker's offline-name
     * button plus its optional footer buttons) is not capped at two. Empty for a list that uses none.
     */
    public List<ExtraButton> extraButtons() {
        return extraButtons;
    }

    /** Fluent builder mirroring the bespoke {@code EntityListView.Builder} so the later shim is mechanical. */
    public static final class Builder {
        private @Nullable Component title;
        private int rows = 6;
        private @Nullable List<Integer> contentSlots;
        private int prevSlot;
        private int nextSlot;
        private @Nullable Material navIcon;
        private @Nullable Material filler;
        private @Nullable Component prevName;
        private @Nullable Component nextName;
        private @Nullable Supplier<List<Object>> entities;
        private @Nullable BiFunction<PlayerRef, Object, ItemStack> iconRenderer;
        private @Nullable BiConsumer<Player, Object> onSelect;
        private OptionalInt createSlot = OptionalInt.empty();
        private Material createIcon = Material.LIME_DYE;
        private @Nullable Component createName;
        private @Nullable Consumer<Player> onCreate;
        private OptionalInt actionSlot = OptionalInt.empty();
        private Material actionIcon = Material.COMPARATOR;
        private @Nullable Component actionName;
        private @Nullable Consumer<Player> onAction;
        private List<ExtraButton> extraButtons = List.of();

        private Builder() {}

        public Builder title(Component title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        public Builder rows(int rows) {
            this.rows = rows;
            return this;
        }

        public Builder contentSlots(List<Integer> contentSlots) {
            this.contentSlots = Objects.requireNonNull(contentSlots, "contentSlots");
            return this;
        }

        /** The previous/next nav slots and the shared icon they are drawn with. */
        public Builder navigation(int prevSlot, int nextSlot, Material navIcon) {
            this.prevSlot = prevSlot;
            this.nextSlot = nextSlot;
            this.navIcon = Objects.requireNonNull(navIcon, "navIcon");
            return this;
        }

        /** The previous- and next-page button names, already resolved from the caller's catalog. */
        public Builder navNames(Component prevName, Component nextName) {
            this.prevName = Objects.requireNonNull(prevName, "prevName");
            this.nextName = Objects.requireNonNull(nextName, "nextName");
            return this;
        }

        public Builder filler(Material filler) {
            this.filler = Objects.requireNonNull(filler, "filler");
            return this;
        }

        public Builder entities(Supplier<List<Object>> entities) {
            this.entities = Objects.requireNonNull(entities, "entities");
            return this;
        }

        public Builder iconRenderer(BiFunction<PlayerRef, Object, ItemStack> iconRenderer) {
            this.iconRenderer = Objects.requireNonNull(iconRenderer, "iconRenderer");
            return this;
        }

        public Builder onSelect(BiConsumer<Player, Object> onSelect) {
            this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
            return this;
        }

        /** Wire the optional create button: its slot, icon, already-resolved name, and the click handler. */
        public Builder onCreate(int slot, Material icon, Component name, Consumer<Player> onCreate) {
            this.createSlot = OptionalInt.of(slot);
            this.createIcon = Objects.requireNonNull(icon, "icon");
            this.createName = Objects.requireNonNull(name, "name");
            this.onCreate = Objects.requireNonNull(onCreate, "onCreate");
            return this;
        }

        /** Wire the optional action button at {@code slot}: a non-entity control with its icon, name, and handler. */
        public Builder onAction(int slot, Material icon, Component name, Consumer<Player> onAction) {
            this.actionSlot = OptionalInt.of(slot);
            this.actionIcon = Objects.requireNonNull(icon, "icon");
            this.actionName = Objects.requireNonNull(name, "name");
            this.onAction = Objects.requireNonNull(onAction, "onAction");
            return this;
        }

        /** Wire any fixed, pre-built buttons beyond the create/action pair, each at its own slot with its own click. */
        public Builder extraButtons(List<ExtraButton> extraButtons) {
            this.extraButtons = List.copyOf(Objects.requireNonNull(extraButtons, "extraButtons"));
            return this;
        }

        /** Build the spec; the constructor validates that every required field was set. */
        public EntityListSpec build() {
            return new EntityListSpec(this);
        }
    }

    /**
     * One fixed, pre-built button the list draws beyond the create/action pair: the slot it sits at, the already-built
     * icon to place there (name and lore baked in by the caller, like a {@code SelectorButton}'s icon), and the handler
     * run with the live viewer when it is clicked. The list renderer places the icon as-is and records the click, so
     * this carries no presentation decision of its own.
     *
     * @param slot the inventory slot the button is drawn at
     * @param icon the prepared icon to place (name/lore already applied)
     * @param onClick invoked with the live viewer when the button is clicked
     */
    public record ExtraButton(int slot, ItemStack icon, Consumer<Player> onClick) {

        public ExtraButton {
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(onClick, "onClick");
        }
    }
}
