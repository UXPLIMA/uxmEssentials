package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A whole menu parsed from its HOCON spec: a title, a row count, its refresh policy, the requirement that gates
 * opening, the actions run on open and close, the items keyed by their spec id, an optional non-chest inventory
 * shape, and the menu's own custom placeholder definitions. Validated up front so the renderer can trust the row
 * count and slot bounds without re-checking.
 *
 * <p>The {@code inventoryType} is carried as a plain operator token, {@code "hopper"}, {@code "dispenser"}, and so
 * on, never a Bukkit {@code InventoryType}, so this model stays pure and plain-JUnit testable. Absent, the menu is
 * the default {@code rows}-based chest. When present, the Bukkit-side façade maps the token to a real inventory
 * shape and falls back to a {@code rows}-based chest if that shape rejects a custom window, which is why {@code rows}
 * and its {@code 1..6} bound are still validated even for a non-chest menu: they size that fallback.
 *
 * <p>The {@code placeholders} map is the menu's own {@code placeholders {}} block: {@code name -> template} pairs
 * that define custom {@code %name%} tokens scoped to this one menu. It is carried verbatim for the Bukkit-side
 * renderer to expand, and is resolved local-first. A menu-scoped token overrides the shared registry (built-ins,
 * data readers, the global custom-placeholder file) for this menu alone. Empty for a menu that declares no block, so
 * such a menu renders byte-identically to before this field existed.
 *
 * <p>The {@code clickCooldownMs} is the menu's own {@code click-cooldown} anti-spam window in milliseconds: two
 * clicks landing closer together than this are treated as one, so a spam-click can't double-fire an item's actions.
 * A value of {@code 0} means the menu sets no window of its own and defers to the server-wide default, so a menu
 * that declares no key behaves exactly as before.
 *
 * <p>The {@code bottomInventory} flag turns the menu into a 90-slot canvas: beyond the chest top it may also place
 * items into the viewer's own 36 inventory slots, addressed as raw slots {@code rows*9 .. rows*9+35}. The Bukkit-side
 * runtime snapshots and restores the player inventory around such a menu; here it only widens the slot-fit bound, and
 * a bottom-inventory menu is chest-only by construction (the raw-slot geometry only holds for a chest). A menu that
 * leaves the flag {@code false}, the default, validates and renders exactly as before.
 *
 * <p>The {@code chestOnly} flag keeps the menu on the chest render path even for a Bedrock viewer the hybrid form
 * renderer would otherwise redirect. A form is a flat button list, so a menu that displays or edits real item stacks
 * (the inventory viewer, a storage-style grid) cannot be represented as one; setting this flag opts such a menu out
 * of the redirect so its Bedrock viewers still get the chest. It is purely a routing hint here, a plain boolean the
 * Bukkit-side façade reads, so this model stays pure and plain-JUnit testable. A menu that leaves it {@code false}
 * the default: is redirected to a form for a Bedrock viewer and renders exactly as before for a Java one.
 *
 * <p>The {@code contents} map is the menu's {@code content {}} block: the slot regions the engine hands to a
 * feature's {@code ContentProvider} instead of drawing itself, keyed by the provider id each region names. Empty for
 * every menu that declares no block, which is every menu drawn entirely from its own items, so such a menu renders
 * and routes clicks exactly as before this block existed.
 *
 * <p>The {@code bedrock} block is the menu's optional {@code bedrock {}} native CustomForm: when present, a Bedrock
 * viewer opening the menu (and not opted out by {@code chestOnly}) gets that explicit form, the form-native
 * dropdown/slider/toggle/multi-input widgets the automatic SimpleForm degradation cannot express, instead of the
 * degraded button list. It is {@link Optional#empty()} for a menu that declares no block, in which case the automatic
 * degradation is unchanged; a Java viewer never sees it either way. Purely spec here, a value the Bukkit-side façade
 * reads, so this model stays pure and plain-JUnit testable.
 */
public record MenuSpec(
        String title,
        int rows,
        RefreshSpec refresh,
        List<Ref> openRequirement,
        List<Ref> openActions,
        List<Ref> closeActions,
        Map<String, MenuItemSpec> items,
        Optional<String> inventoryType,
        Map<String, String> placeholders,
        long clickCooldownMs,
        boolean bottomInventory,
        boolean chestOnly,
        Optional<BedrockFormSpec> bedrock,
        Map<String, ContentRegionSpec> contents) {

    public MenuSpec {
        Objects.requireNonNull(title, "title");
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be in 1..6: " + rows);
        }
        Objects.requireNonNull(refresh, "refresh");
        openRequirement = List.copyOf(Objects.requireNonNull(openRequirement, "openRequirement"));
        openActions = List.copyOf(Objects.requireNonNull(openActions, "openActions"));
        closeActions = List.copyOf(Objects.requireNonNull(closeActions, "closeActions"));
        items = Map.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(inventoryType, "inventoryType");
        placeholders = Map.copyOf(Objects.requireNonNull(placeholders, "placeholders"));
        if (clickCooldownMs < 0) {
            throw new IllegalArgumentException("clickCooldownMs must be >= 0: " + clickCooldownMs);
        }
        Objects.requireNonNull(bedrock, "bedrock");
        contents = Map.copyOf(Objects.requireNonNull(contents, "contents"));
        checkSlotsFit(items, rows, bottomInventory);
        checkRegionsFit(contents, rows, bottomInventory);
    }

    /**
     * The thirteen-argument shape that carries a Bedrock block but no content region, kept so every existing
     * {@code new MenuSpec(...)} call site compiles unchanged. It delegates to the canonical constructor with no
     * regions, i.e. a menu the engine draws and routes entirely from its own items.
     */
    public MenuSpec(
            String title,
            int rows,
            RefreshSpec refresh,
            List<Ref> openRequirement,
            List<Ref> openActions,
            List<Ref> closeActions,
            Map<String, MenuItemSpec> items,
            Optional<String> inventoryType,
            Map<String, String> placeholders,
            long clickCooldownMs,
            boolean bottomInventory,
            boolean chestOnly,
            Optional<BedrockFormSpec> bedrock) {
        this(
                title,
                rows,
                refresh,
                openRequirement,
                openActions,
                closeActions,
                items,
                inventoryType,
                placeholders,
                clickCooldownMs,
                bottomInventory,
                chestOnly,
                bedrock,
                Map.of());
    }

    /** The content region covering {@code slot}, or empty when the slot is ordinary chrome. */
    public Optional<ContentRegionSpec> regionAt(int slot) {
        for (ContentRegionSpec region : contents.values()) {
            if (region.covers(slot)) {
                return Optional.of(region);
            }
        }
        return Optional.empty();
    }

    /**
     * The twelve-argument shape that carries a chest-only routing hint but no {@code bedrock {}} block, kept so every
     * existing {@code new MenuSpec(...)} call site compiles unchanged. It delegates to the canonical constructor with
     * an empty Bedrock block, so a Bedrock viewer of such a menu gets the automatic SimpleForm degradation exactly as
     * before this block existed.
     */
    public MenuSpec(
            String title,
            int rows,
            RefreshSpec refresh,
            List<Ref> openRequirement,
            List<Ref> openActions,
            List<Ref> closeActions,
            Map<String, MenuItemSpec> items,
            Optional<String> inventoryType,
            Map<String, String> placeholders,
            long clickCooldownMs,
            boolean bottomInventory,
            boolean chestOnly) {
        this(
                title,
                rows,
                refresh,
                openRequirement,
                openActions,
                closeActions,
                items,
                inventoryType,
                placeholders,
                clickCooldownMs,
                bottomInventory,
                chestOnly,
                Optional.empty());
    }

    /**
     * The eleven-argument shape that carries a bottom-inventory flag but no chest-only routing hint, kept so every
     * existing {@code new MenuSpec(...)} call site, the loader's older path and the record test fixtures, compiles
     * unchanged. It delegates to the twelve-argument constructor with the chest-only flag off, so the menu is a form
     * candidate for a Bedrock viewer exactly as before this hint existed.
     */
    public MenuSpec(
            String title,
            int rows,
            RefreshSpec refresh,
            List<Ref> openRequirement,
            List<Ref> openActions,
            List<Ref> closeActions,
            Map<String, MenuItemSpec> items,
            Optional<String> inventoryType,
            Map<String, String> placeholders,
            long clickCooldownMs,
            boolean bottomInventory) {
        this(
                title,
                rows,
                refresh,
                openRequirement,
                openActions,
                closeActions,
                items,
                inventoryType,
                placeholders,
                clickCooldownMs,
                bottomInventory,
                false);
    }

    /**
     * The ten-argument shape that carries a click cooldown but paints only the chest top, kept so every existing
     * {@code new MenuSpec(...)} call site compiles unchanged. It delegates to the canonical constructor with the
     * bottom-inventory flag off: an ordinary chest menu that never touches the player inventory.
     */
    public MenuSpec(
            String title,
            int rows,
            RefreshSpec refresh,
            List<Ref> openRequirement,
            List<Ref> openActions,
            List<Ref> closeActions,
            Map<String, MenuItemSpec> items,
            Optional<String> inventoryType,
            Map<String, String> placeholders,
            long clickCooldownMs) {
        this(
                title,
                rows,
                refresh,
                openRequirement,
                openActions,
                closeActions,
                items,
                inventoryType,
                placeholders,
                clickCooldownMs,
                false);
    }

    /**
     * The nine-argument shape that carries local placeholders but no click cooldown, kept so the loader and any other
     * with-placeholders caller compiles unchanged. It delegates to the ten-argument constructor with a zero cooldown
     * a menu that defers its anti-spam window to the server-wide default.
     */
    public MenuSpec(
            String title,
            int rows,
            RefreshSpec refresh,
            List<Ref> openRequirement,
            List<Ref> openActions,
            List<Ref> closeActions,
            Map<String, MenuItemSpec> items,
            Optional<String> inventoryType,
            Map<String, String> placeholders) {
        this(title, rows, refresh, openRequirement, openActions, closeActions, items, inventoryType, placeholders, 0L);
    }

    /**
     * The eight-argument shape that carries an inventory type but no local placeholders, kept so the loader's
     * inventory-type call and any other with-inventory-type caller compiles unchanged. It delegates to the canonical
     * constructor with an empty local placeholder map: a menu without its own {@code placeholders {}} block.
     */
    public MenuSpec(
            String title,
            int rows,
            RefreshSpec refresh,
            List<Ref> openRequirement,
            List<Ref> openActions,
            List<Ref> closeActions,
            Map<String, MenuItemSpec> items,
            Optional<String> inventoryType) {
        this(title, rows, refresh, openRequirement, openActions, closeActions, items, inventoryType, Map.of());
    }

    /**
     * The historic seven-argument shape, kept so every existing {@code new MenuSpec(...)} call-site: the loader's
     * chest path and the engine's list/confirm/selector/editor holder specs: compiles unchanged. It delegates to the
     * eight-argument constructor with no inventory type, i.e. the default {@code rows}-based chest, and so on to the
     * canonical constructor with an empty local placeholder map.
     */
    public MenuSpec(
            String title,
            int rows,
            RefreshSpec refresh,
            List<Ref> openRequirement,
            List<Ref> openActions,
            List<Ref> closeActions,
            Map<String, MenuItemSpec> items) {
        this(title, rows, refresh, openRequirement, openActions, closeActions, items, Optional.empty());
    }

    /**
     * The count of the viewer's own inventory slots a bottom-inventory menu may additionally paint into, the 27
     * main slots plus the 9 hotbar slots shown below the chest top.
     */
    private static final int BOTTOM_SLOTS = 36;

    private static void checkRegionsFit(Map<String, ContentRegionSpec> contents, int rows, boolean bottomInventory) {
        int capacity = rows * 9 + (bottomInventory ? BOTTOM_SLOTS : 0);
        for (ContentRegionSpec region : contents.values()) {
            for (int slot : region.slots().slots()) {
                if (slot >= capacity) {
                    throw new IllegalArgumentException(
                            "content region '" + region.id() + "' slot " + slot + " exceeds capacity " + capacity);
                }
            }
        }
    }

    private static void checkSlotsFit(Map<String, MenuItemSpec> items, int rows, boolean bottomInventory) {
        int capacity = rows * 9 + (bottomInventory ? BOTTOM_SLOTS : 0);
        for (MenuItemSpec item : items.values()) {
            for (int slot : item.slots().slots()) {
                if (slot >= capacity) {
                    throw new IllegalArgumentException("slot " + slot + " exceeds capacity " + capacity);
                }
            }
        }
    }
}
