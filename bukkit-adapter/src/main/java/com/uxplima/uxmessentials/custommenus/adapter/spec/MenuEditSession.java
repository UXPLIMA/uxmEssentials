package com.uxplima.uxmessentials.custommenus.adapter.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.OpenCommandSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.BedrockFormSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.LoreMode;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RefreshSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Requirement;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RequirementSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RichMeta;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import org.jspecify.annotations.Nullable;

/**
 * The mutable working copy of a {@link MenuSpec} the in-game menu editor edits before it writes. It is the middle of
 * the editor's spec service. The loader turns a file into an immutable {@link MenuSpec}, this clones that into an
 * editable model an operator mutates through the GUI, and {@link #toSpec()} freezes it back into an immutable spec the
 * {@link MenuSpecWriter} serialises. So an edit is a clone, a sequence of mutations, and a re-freeze, never a
 * hand-built HOCON string.
 *
 * <p>Bukkit-free like the spec model it holds: it references only the {@code spec/} records and the menu's
 * {@code command {}} value object, so it is exercised by plain JUnit and can be mutated off the tick thread. It carries
 * the whole menu surface (the menu-level fields, the item map, and the optional open-command block) even though the
 * P1 editor only wires the menu-level setters and the item CRUD; the later phases (slot grid, item and action editors)
 * mutate the rest through the same model.
 *
 * <p>Not thread-safe: a session belongs to one editing flow. Every mutation validates lazily through {@link #toSpec()},
 * which re-runs the {@link MenuSpec} constructor's row-bound and slot-fit checks, so an edit that would leave the menu
 * invalid (a slot past the last row) surfaces at freeze time rather than being silently written.
 */
public final class MenuEditSession {

    /** The viewer's own inventory slots a bottom-inventory menu additionally paints into, mirrors {@code MenuSpec}. */
    private static final int BOTTOM_SLOTS = 36;

    private String title;
    private int rows;
    private RefreshSpec refresh;
    private List<Ref> openRequirement;
    private List<Ref> openActions;
    private List<Ref> closeActions;
    private final LinkedHashMap<String, MenuItemSpec> items;
    private @Nullable String inventoryType;
    private Map<String, String> placeholders;
    private long clickCooldownMs;
    private boolean bottomInventory;
    private boolean chestOnly;
    private @Nullable BedrockFormSpec bedrock;
    private @Nullable OpenCommandSpec command;

    private MenuEditSession(MenuSpec spec, @Nullable OpenCommandSpec command) {
        Objects.requireNonNull(spec, "spec");
        this.title = spec.title();
        this.rows = spec.rows();
        this.refresh = spec.refresh();
        this.openRequirement = List.copyOf(spec.openRequirement());
        this.openActions = List.copyOf(spec.openActions());
        this.closeActions = List.copyOf(spec.closeActions());
        this.items = new LinkedHashMap<>(spec.items());
        this.inventoryType = spec.inventoryType().orElse(null);
        this.placeholders = new LinkedHashMap<>(spec.placeholders());
        this.clickCooldownMs = spec.clickCooldownMs();
        this.bottomInventory = spec.bottomInventory();
        this.chestOnly = spec.chestOnly();
        this.bedrock = spec.bedrock().orElse(null);
        this.command = command;
    }

    /** Start a session over {@code spec} with no open-command block. */
    public static MenuEditSession from(MenuSpec spec) {
        return new MenuEditSession(spec, null);
    }

    /** Start a session over {@code spec} carrying its optional {@code command {}} open-command block. */
    public static MenuEditSession from(MenuSpec spec, @Nullable OpenCommandSpec command) {
        return new MenuEditSession(spec, command);
    }

    // --- item CRUD ----------------------------------------------------------------------------------------------

    /** Add (or replace) the item stored under {@code id}; a repeat id overwrites, mirroring the loader's map. */
    public MenuEditSession addItem(String id, MenuItemSpec item) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(item, "item");
        items.put(id, item);
        return this;
    }

    /** Remove the item stored under {@code id}; a no-op when no item carries that id. */
    public MenuEditSession removeItem(String id) {
        Objects.requireNonNull(id, "id");
        items.remove(id);
        return this;
    }

    /**
     * Move the item stored under {@code id} to {@code slots}, keeping every other field of the item. A no-op when no
     * item carries that id: the grid editor never moves a slot that holds nothing.
     */
    public MenuEditSession moveItem(String id, SlotSet slots) {
        Objects.requireNonNull(slots, "slots");
        return updateItem(id, item -> item.withSlots(slots));
    }

    /**
     * Replace the item stored under {@code id} with {@code mutation}'s result, keeping the map key, the primitive the
     * item property editor's every field setter runs through, since {@link MenuItemSpec} is immutable. A no-op when no
     * item carries that id, so a stale editor click never re-inserts a removed item.
     */
    public MenuEditSession updateItem(String id, UnaryOperator<MenuItemSpec> mutation) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(mutation, "mutation");
        MenuItemSpec current = items.get(id);
        if (current != null) {
            items.put(id, Objects.requireNonNull(mutation.apply(current), "mutation result"));
        }
        return this;
    }

    /** The item stored under {@code id}, or empty when none is: the read the grid editor resolves a clicked slot by. */
    public Optional<MenuItemSpec> item(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(items.get(id));
    }

    // --- per-item field setters (the item property editor mutates one field per click) --------------------------

    /** Set the raw material token of the item under {@code id}, a plain name, {@code head:…}, or a {@code b64:…} stack. */
    public MenuEditSession setMaterial(String id, String material) {
        Objects.requireNonNull(material, "material");
        return updateItem(id, item -> item.withMaterial(material));
    }

    /** Set the display name of the item under {@code id}; a blank name resets it to the base icon's own name. */
    public MenuEditSession setName(String id, String name) {
        Objects.requireNonNull(name, "name");
        return updateItem(id, item -> item.withName(name));
    }

    /** Replace the lore lines of the item under {@code id} (add / edit / remove / reorder all funnel through here). */
    public MenuEditSession setLore(String id, List<String> lore) {
        Objects.requireNonNull(lore, "lore");
        return updateItem(id, item -> item.withLore(lore));
    }

    /** Set the slots the item under {@code id} occupies: the item editor's slot-assignment field. */
    public MenuEditSession setSlots(String id, SlotSet slots) {
        Objects.requireNonNull(slots, "slots");
        return updateItem(id, item -> item.withSlots(slots));
    }

    /** Set the contention priority of the item under {@code id}. */
    public MenuEditSession setPriority(String id, int priority) {
        return updateItem(id, item -> item.withPriority(priority));
    }

    /** Set the pagination role of the item under {@code id}. */
    public MenuEditSession setType(String id, ItemType type) {
        Objects.requireNonNull(type, "type");
        return updateItem(id, item -> item.withType(type));
    }

    /** Set how the item under {@code id} combines its spec lore with the base icon's lore. */
    public MenuEditSession setLoreMode(String id, LoreMode loreMode) {
        Objects.requireNonNull(loreMode, "loreMode");
        return updateItem(id, item -> item.withLoreMode(loreMode));
    }

    /** Set the rendered stack amount of the item under {@code id}. */
    public MenuEditSession setAmount(String id, int amount) {
        return updateItem(id, item -> item.withDecor(item.decor().withAmount(amount)));
    }

    /** Set (or clear, with {@link Optional#empty()}) the custom-model-data override of the item under {@code id}. */
    public MenuEditSession setModelData(String id, Optional<Integer> modelData) {
        Objects.requireNonNull(modelData, "modelData");
        return updateItem(id, item -> item.withDecor(item.decor().withModelData(modelData)));
    }

    /** Turn the enchant glow of the item under {@code id} on or off. */
    public MenuEditSession setGlow(String id, boolean glow) {
        return updateItem(id, item -> item.withDecor(item.decor().withGlow(glow)));
    }

    /**
     * Whether the client may write its own tooltip lines under the item's lore: the item editor's toggle. Off is
     * the default a menu icon wants, so this writes the key only to say something other than the default.
     */
    public MenuEditSession setVanillaTooltipHidden(String id, boolean hidden) {
        return updateItem(id, item -> {
            RichMeta meta = item.decor().meta();
            return item.withDecor(item.decor()
                    .withMeta(meta.withComponents(
                            meta.components().withHideVanillaTooltip(hidden ? Optional.empty() : Optional.of(false)))));
        });
    }

    /** Replace the item-flag tokens of the item under {@code id}: the item editor's per-flag toggles. */
    public MenuEditSession setFlags(String id, List<String> flags) {
        Objects.requireNonNull(flags, "flags");
        return updateItem(id, item -> item.withDecor(item.decor().withFlagTokens(flags)));
    }

    // --- per-gesture click actions (the click-actions editor mutates one gesture per edit) -----------------------

    /** The action refs bound to {@code kind} on the item under {@code id}: the gesture's own list, not the {@code ANY} merge. */
    public List<Ref> clickActions(String id, ClickKind kind) {
        Objects.requireNonNull(kind, "kind");
        return item(id).map(item -> item.click().actions().getOrDefault(kind, List.of()))
                .orElseGet(List::of);
    }

    /** Replace the whole action list bound to {@code kind}: the workhorse the click-actions ref-list editor writes through. */
    public MenuEditSession setClickActions(String id, ClickKind kind, List<Ref> refs) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(refs, "refs");
        return updateItem(id, item -> item.withClick(item.click().withGestureActions(kind, refs)));
    }

    /** Append {@code ref} to {@code kind}'s action list on the item under {@code id}. */
    public MenuEditSession addAction(String id, ClickKind kind, Ref ref) {
        Objects.requireNonNull(ref, "ref");
        List<Ref> next = new ArrayList<>(clickActions(id, kind));
        next.add(ref);
        return setClickActions(id, kind, next);
    }

    /** Remove the action at {@code index} from {@code kind}'s list on the item under {@code id}; out-of-range is a no-op. */
    public MenuEditSession removeAction(String id, ClickKind kind, int index) {
        List<Ref> next = new ArrayList<>(clickActions(id, kind));
        if (index >= 0 && index < next.size()) {
            next.remove(index);
        }
        return setClickActions(id, kind, next);
    }

    /** Move the action at {@code index} by {@code direction} (−1 up, +1 down) within {@code kind}'s list; a bad move is a no-op. */
    public MenuEditSession moveAction(String id, ClickKind kind, int index, int direction) {
        List<Ref> next = new ArrayList<>(clickActions(id, kind));
        int target = index + direction;
        if (index >= 0 && index < next.size() && target >= 0 && target < next.size()) {
            next.add(target, next.remove(index));
        }
        return setClickActions(id, kind, next);
    }

    /** Set {@code kind}'s requirement block on the item under {@code id}, the per-gesture click gate. */
    public MenuEditSession setClickRequirement(String id, ClickKind kind, RequirementSpec requirement) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(requirement, "requirement");
        return updateItem(id, item -> item.withClick(item.click().withGestureRequirement(kind, requirement)));
    }

    // --- view requirements (the requirements editor gates an item's visibility) ---------------------------------

    /** The condition refs of the item under {@code id}'s {@code view} gate, in order: the ref-list editor's read. */
    public List<Ref> viewConditions(String id) {
        return item(id).map(item -> item.view().requirements().stream()
                        .map(Requirement::condition)
                        .toList())
                .orElseGet(List::of);
    }

    /**
     * Replace the item under {@code id}'s view requirements from a flat condition-ref list: the ref-list editor's
     * write. Each ref becomes a plain mandatory, non-inverted {@link Requirement}; a hand-written {@code !condition} or
     * optional flag is normalised away by a GUI edit, so an author who needs inversion keeps it in the file.
     */
    public MenuEditSession setViewConditions(String id, List<Ref> conditions) {
        Objects.requireNonNull(conditions, "conditions");
        List<Requirement> requirements =
                conditions.stream().map(ref -> new Requirement(ref, false)).toList();
        return updateItem(id, item -> item.withView(item.view().withRequirements(requirements)));
    }

    /** Append a mandatory, non-inverted condition {@code requirement} to the item under {@code id}'s view gate. */
    public MenuEditSession addRequirement(String id, Ref requirement) {
        Objects.requireNonNull(requirement, "requirement");
        List<Ref> next = new ArrayList<>(viewConditions(id));
        next.add(requirement);
        return setViewConditions(id, next);
    }

    /** Remove the view requirement at {@code index} from the item under {@code id}; out-of-range is a no-op. */
    public MenuEditSession removeRequirement(String id, int index) {
        List<Ref> next = new ArrayList<>(viewConditions(id));
        if (index >= 0 && index < next.size()) {
            next.remove(index);
        }
        return setViewConditions(id, next);
    }

    /** Move the view requirement at {@code index} by {@code direction} (−1 up, +1 down); a bad move is a no-op. */
    public MenuEditSession moveRequirement(String id, int index, int direction) {
        List<Ref> next = new ArrayList<>(viewConditions(id));
        int target = index + direction;
        if (index >= 0 && index < next.size() && target >= 0 && target < next.size()) {
            next.add(target, next.remove(index));
        }
        return setViewConditions(id, next);
    }

    /** Set the item under {@code id}'s view {@code minimum}: the AND / OR / N-of-M combinator over its requirements. */
    public MenuEditSession setViewMinimum(String id, int minimum) {
        return updateItem(id, item -> item.withView(item.view().withMinimum(minimum)));
    }

    /** Set the item under {@code id}'s view {@code deny} action list: what runs when the visibility gate fails. */
    public MenuEditSession setViewDeny(String id, List<Ref> deny) {
        Objects.requireNonNull(deny, "deny");
        return updateItem(id, item -> item.withView(item.view().withDeny(deny)));
    }

    /** The current item map, as an unmodifiable view in insertion order. */
    public Map<String, MenuItemSpec> items() {
        return Map.copyOf(items);
    }

    // --- menu-level setters -------------------------------------------------------------------------------------

    public MenuEditSession setTitle(String title) {
        this.title = Objects.requireNonNull(title, "title");
        return this;
    }

    /**
     * Resize the menu to {@code rows}. Shrinking can leave an item in a slot the smaller menu no longer has; rather
     * than let {@link #toSpec()} reject the whole menu, any item that would fall out of range is dropped here, so the
     * model stays valid after every resize. The editor surfaces the shrink through the redrawn (now smaller) item
     * count. The dropped items are gone from the working copy, not the file, until the operator saves.
     */
    public MenuEditSession setRows(int rows) {
        this.rows = rows;
        dropOrphanedItems();
        return this;
    }

    /** Set the non-chest inventory shape token, or {@code null} to return to the default {@code rows}-based chest. */
    public MenuEditSession setInventoryType(@Nullable String inventoryType) {
        this.inventoryType = inventoryType;
        return this;
    }

    public MenuEditSession setRefresh(RefreshSpec refresh) {
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        return this;
    }

    /**
     * Set the refresh policy from an enabled flag and an interval. The two properties the menu editor surfaces as a
     * toggle and a stepper. Enabling with a non-positive interval is snapped up to one tick, since {@link RefreshSpec}
     * forbids an enabled zero-interval loop, so the toggle can flip refresh on without the operator first setting an
     * interval.
     */
    public MenuEditSession setRefresh(boolean enabled, int intervalTicks) {
        this.refresh = enabled
                ? new RefreshSpec(true, Math.max(1, intervalTicks))
                : new RefreshSpec(false, Math.max(0, intervalTicks));
        return this;
    }

    public MenuEditSession setPlaceholders(Map<String, String> placeholders) {
        this.placeholders = new LinkedHashMap<>(Objects.requireNonNull(placeholders, "placeholders"));
        return this;
    }

    public MenuEditSession setClickCooldownMs(long clickCooldownMs) {
        this.clickCooldownMs = clickCooldownMs;
        return this;
    }

    public MenuEditSession setBottomInventory(boolean bottomInventory) {
        this.bottomInventory = bottomInventory;
        // Turning the bottom canvas off shrinks the addressable range, so drop any item that was placed in it.
        dropOrphanedItems();
        return this;
    }

    public MenuEditSession setChestOnly(boolean chestOnly) {
        this.chestOnly = chestOnly;
        return this;
    }

    public MenuEditSession setBedrock(@Nullable BedrockFormSpec bedrock) {
        this.bedrock = bedrock;
        return this;
    }

    public MenuEditSession setOpenRequirement(List<Ref> openRequirement) {
        this.openRequirement = List.copyOf(Objects.requireNonNull(openRequirement, "openRequirement"));
        return this;
    }

    public MenuEditSession setOpenActions(List<Ref> openActions) {
        this.openActions = List.copyOf(Objects.requireNonNull(openActions, "openActions"));
        return this;
    }

    public MenuEditSession setCloseActions(List<Ref> closeActions) {
        this.closeActions = List.copyOf(Objects.requireNonNull(closeActions, "closeActions"));
        return this;
    }

    /** Set (or clear, with {@code null}) the {@code command {}} open-command block written beside the menu. */
    public MenuEditSession setCommand(@Nullable OpenCommandSpec command) {
        this.command = command;
        return this;
    }

    // --- reads / freeze -----------------------------------------------------------------------------------------

    public String title() {
        return title;
    }

    public int rows() {
        return rows;
    }

    public Optional<String> inventoryType() {
        return Optional.ofNullable(inventoryType);
    }

    public long clickCooldownMs() {
        return clickCooldownMs;
    }

    public boolean bottomInventory() {
        return bottomInventory;
    }

    public boolean chestOnly() {
        return chestOnly;
    }

    /** The menu's refresh policy: the toggle-and-interval pair the menu editor's two refresh rows read. */
    public RefreshSpec refresh() {
        return refresh;
    }

    /** The condition refs gating who may open the menu, in order: the open-requirement ref-list editor's read. */
    public List<Ref> openRequirement() {
        return openRequirement;
    }

    /** The action refs run when the menu opens, in order: the open-actions ref-list editor's read. */
    public List<Ref> openActions() {
        return openActions;
    }

    /** The action refs run when the menu closes, in order: the close-actions ref-list editor's read. */
    public List<Ref> closeActions() {
        return closeActions;
    }

    /** The open-command block riding this session, or empty when the menu declares none. */
    public Optional<OpenCommandSpec> command() {
        return Optional.ofNullable(command);
    }

    /** Drop any item whose slots no longer fit the menu, so a shrink never leaves {@link #toSpec()} unable to validate. */
    private void dropOrphanedItems() {
        int capacity = rows * 9 + (bottomInventory ? BOTTOM_SLOTS : 0);
        items.values().removeIf(item -> item.slots().slots().stream().anyMatch(slot -> slot >= capacity));
    }

    /**
     * Freeze the current model into an immutable {@link MenuSpec}. Re-runs the record's validation (rows in {@code
     * 1..6}, every slot within the menu's capacity), so a mutation that would produce an invalid menu throws here
     * rather than writing a spec the loader would reject.
     */
    public MenuSpec toSpec() {
        return new MenuSpec(
                title,
                rows,
                refresh,
                openRequirement,
                openActions,
                closeActions,
                Map.copyOf(items),
                Optional.ofNullable(inventoryType),
                Map.copyOf(placeholders),
                clickCooldownMs,
                bottomInventory,
                chestOnly,
                Optional.ofNullable(bedrock));
    }
}
