package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One item in a menu spec. It declares the slots it occupies, a priority used when several items contend for the
 * same slot, the raw material/name/lore text (a {@code @key}, an inline literal, or a {@code %placeholder%}
 * resolved later), its decoration, how its lore combines with the base icon's own lore, the {@link RequirementSpec}
 * view block that gates visibility, click handling, whether it re-renders on refresh, an optional list expansion, and
 * its pagination role.
 *
 * <p>The {@code view} gate is a {@link RequirementSpec}, the same block that backs a click gesture, so an item can
 * gate visibility on a minimum (OR / N-of-M) or an inverted condition, not just a flat AND list. A flat view list
 * lifts into an all-mandatory block through {@link RequirementSpec#allOf(List)}, which the {@code List<Ref>}-taking
 * constructors below do for their callers, so a spec that wrote a plain condition list keeps its exact meaning.
 *
 * <p>The {@code itemDrag} component, when present, makes the item a drag target: a click on its slot while the
 * viewer holds a matching item on their cursor runs its own actions instead of the ordinary click, and optionally
 * consumes the item. It is empty for an ordinary item, which is the common case, so the delegating constructors
 * default it to {@link Optional#empty()} and every existing call site keeps compiling unchanged.
 */
public record MenuItemSpec(
        SlotSet slots,
        int priority,
        String material,
        String name,
        List<String> lore,
        ItemDecor decor,
        LoreMode loreMode,
        RequirementSpec view,
        ClickSpec click,
        boolean update,
        Optional<ListSpec> list,
        ItemType type,
        Optional<ItemDragSpec> itemDrag) {

    public MenuItemSpec {
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
        Objects.requireNonNull(decor, "decor");
        Objects.requireNonNull(loreMode, "loreMode");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(click, "click");
        Objects.requireNonNull(list, "list");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(itemDrag, "itemDrag");
    }

    /**
     * The twelve-argument form that takes a {@link RequirementSpec} view but no item-drag binding, retained so a call
     * site that only wanted the richer view block keeps compiling unchanged: item-drag defaults to
     * {@link Optional#empty()}. Only the loader reaches for the canonical thirteen-argument form, and only when a spec
     * declares an {@code item-drag} block.
     */
    public MenuItemSpec(
            SlotSet slots,
            int priority,
            String material,
            String name,
            List<String> lore,
            ItemDecor decor,
            LoreMode loreMode,
            RequirementSpec view,
            ClickSpec click,
            boolean update,
            Optional<ListSpec> list,
            ItemType type) {
        this(slots, priority, material, name, lore, decor, loreMode, view, click, update, list, type, Optional.empty());
    }

    /**
     * The twelve-argument form that takes a flat {@code List<Ref>} view, retained so a call site holding a plain
     * condition list keeps compiling unchanged: the list is lifted into an all-mandatory AND {@link RequirementSpec}
     * via {@link RequirementSpec#allOf(List)}, the historic view meaning, and item-drag defaults to
     * {@link Optional#empty()}. Only the loader reaches for the canonical form, and only to build a view block that
     * carries a minimum or inversion, or an item-drag binding.
     */
    public MenuItemSpec(
            SlotSet slots,
            int priority,
            String material,
            String name,
            List<String> lore,
            ItemDecor decor,
            LoreMode loreMode,
            List<Ref> view,
            ClickSpec click,
            boolean update,
            Optional<ListSpec> list,
            ItemType type) {
        this(
                slots,
                priority,
                material,
                name,
                lore,
                decor,
                loreMode,
                RequirementSpec.allOf(view),
                click,
                update,
                list,
                type,
                Optional.empty());
    }

    /**
     * The original eleven-argument form, retained so every existing call site keeps compiling unchanged: it uses
     * {@link LoreMode#REPLACE}, the historic behaviour where the spec lore is the whole lore, and lifts its flat
     * {@code List<Ref>} view into an all-mandatory block via {@link RequirementSpec#allOf(List)}. Only the loader
     * builds the canonical form, and only when a spec declares a {@code lore-mode} or a richer view block.
     */
    public MenuItemSpec(
            SlotSet slots,
            int priority,
            String material,
            String name,
            List<String> lore,
            ItemDecor decor,
            List<Ref> view,
            ClickSpec click,
            boolean update,
            Optional<ListSpec> list,
            ItemType type) {
        this(
                slots,
                priority,
                material,
                name,
                lore,
                decor,
                LoreMode.REPLACE,
                RequirementSpec.allOf(view),
                click,
                update,
                list,
                type,
                Optional.empty());
    }

    // --- immutable field-copy helpers (the in-game item editor rebuilds one field at a time) ---

    /** A copy occupying {@code slots}, every other field unchanged, the canonical way to relocate an item. */
    public MenuItemSpec withSlots(SlotSet slots) {
        return new MenuItemSpec(
                slots, priority, material, name, lore, decor, loreMode, view, click, update, list, type, itemDrag);
    }

    /** A copy with a new contention {@code priority}, every other field unchanged. */
    public MenuItemSpec withPriority(int priority) {
        return new MenuItemSpec(
                slots, priority, material, name, lore, decor, loreMode, view, click, update, list, type, itemDrag);
    }

    /** A copy with a new raw {@code material} token ({@code STONE}, {@code head:…}, {@code b64:…}), every other field kept. */
    public MenuItemSpec withMaterial(String material) {
        return new MenuItemSpec(
                slots, priority, material, name, lore, decor, loreMode, view, click, update, list, type, itemDrag);
    }

    /** A copy with a new display {@code name} (a MiniMessage/legacy line, or blank to reset), every other field kept. */
    public MenuItemSpec withName(String name) {
        return new MenuItemSpec(
                slots, priority, material, name, lore, decor, loreMode, view, click, update, list, type, itemDrag);
    }

    /** A copy carrying a new {@code lore} line list, every other field unchanged. */
    public MenuItemSpec withLore(List<String> lore) {
        return new MenuItemSpec(
                slots, priority, material, name, lore, decor, loreMode, view, click, update, list, type, itemDrag);
    }

    /** A copy carrying a new {@code decor} block (amount / model-data / glow / flags / rich meta), every other field kept. */
    public MenuItemSpec withDecor(ItemDecor decor) {
        return new MenuItemSpec(
                slots, priority, material, name, lore, decor, loreMode, view, click, update, list, type, itemDrag);
    }

    /** A copy with a new {@code loreMode} (replace / append / prepend), every other field unchanged. */
    public MenuItemSpec withLoreMode(LoreMode loreMode) {
        return new MenuItemSpec(
                slots, priority, material, name, lore, decor, loreMode, view, click, update, list, type, itemDrag);
    }

    /** A copy with a new pagination {@code type} (none / next / previous / jump), every other field unchanged. */
    public MenuItemSpec withType(ItemType type) {
        return new MenuItemSpec(
                slots, priority, material, name, lore, decor, loreMode, view, click, update, list, type, itemDrag);
    }

    /** A copy carrying a new {@code view} visibility gate (the requirement/action editor rebuilds it), every other field kept. */
    public MenuItemSpec withView(RequirementSpec view) {
        return new MenuItemSpec(
                slots, priority, material, name, lore, decor, loreMode, view, click, update, list, type, itemDrag);
    }

    /** A copy carrying a new {@code click} handling block (the per-gesture action editor rebuilds it), every other field kept. */
    public MenuItemSpec withClick(ClickSpec click) {
        return new MenuItemSpec(
                slots, priority, material, name, lore, decor, loreMode, view, click, update, list, type, itemDrag);
    }
}
