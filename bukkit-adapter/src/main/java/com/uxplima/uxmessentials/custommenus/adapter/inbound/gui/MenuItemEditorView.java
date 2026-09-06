package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.google.common.base.Splitter;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuEditSession;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.LoreMode;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ActionProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EnumProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ListProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ListPropertyLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ListPropertyText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.NumberProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.TextProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ToggleProperty;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.SerializedItems;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The per-item property editor of the {@code /menu editor}: clicking a filled cell on the {@link MenuGridView} grid
 * opens this window to change that item's material, name, lore, decor and slots. It is a thin consumer of the shared
 * {@link EntityEditorView}. Every field is one {@link EditableProperty} button wired to a {@link MenuEditSession}
 * setter, the anvil prompts ride {@link TextInput}, the lore-line list is a {@link ListProperty}, and the two
 * selectors are {@link EnumProperty} children, so no raw Bukkit inventory is ever built here and the editor stays on
 * the menu engine like every other editor surface.
 *
 * <p>The editor mutates the grid's per-viewer working copy in place: each property reads the item fresh from the
 * session on every draw (so a change shows on re-render) and writes back through the session's field setters, never
 * the live menu. The live menu changes only when the operator saves on the grid or overview (the P0 validate → write →
 * hot-reload path). Back returns to the grid, which re-renders with the edit. There is no delete button, clearing a
 * cell is a grid gesture, not an item-editor field.
 */
@NullMarked
public final class MenuItemEditorView {

    private static final String MODULE = "custommenus";
    private static final String LAYOUT = "menu-item-editor";
    private static final String TEXT_INPUT_KEY = "editor.text-field";
    private static final String LIST_INPUT_KEY = "editor.list-entry";

    /** The property-button slots, in the order {@link #properties} builds them; a 6-row grid with room to spare. */
    private static final List<Integer> PROPERTY_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42,
            43);

    private static final int BACK_SLOT = 49;

    /** The item-flag tokens exposed as toggles, each a Bukkit {@code ItemFlag} name the renderer resolves fail-soft. */
    private static final List<String> FLAG_TOKENS = List.of(
            "HIDE_ENCHANTS",
            "HIDE_ATTRIBUTES",
            "HIDE_UNBREAKABLE",
            "HIDE_DYE",
            "HIDE_ARMOR_TRIM",
            "HIDE_DESTROYS",
            "HIDE_PLACED_ON");

    /** The catalog label each flag toggle carries, so no flag name is ever an inline user-facing literal. */
    private static final Map<String, MessageKey> FLAG_LABELS = Map.of(
            "HIDE_ENCHANTS", CustomMenusMessageKey.MENU_ITEM_EDITOR_FLAG_HIDE_ENCHANTS,
            "HIDE_ATTRIBUTES", CustomMenusMessageKey.MENU_ITEM_EDITOR_FLAG_HIDE_ATTRIBUTES,
            "HIDE_UNBREAKABLE", CustomMenusMessageKey.MENU_ITEM_EDITOR_FLAG_HIDE_UNBREAKABLE,
            "HIDE_DYE", CustomMenusMessageKey.MENU_ITEM_EDITOR_FLAG_HIDE_DYE,
            "HIDE_ARMOR_TRIM", CustomMenusMessageKey.MENU_ITEM_EDITOR_FLAG_HIDE_ARMOR_TRIM,
            "HIDE_DESTROYS", CustomMenusMessageKey.MENU_ITEM_EDITOR_FLAG_HIDE_DESTROYS,
            "HIDE_PLACED_ON", CustomMenusMessageKey.MENU_ITEM_EDITOR_FLAG_HIDE_PLACED_ON);

    private static final long MAX_MODEL_DATA = 9_999_999L;

    /** The stand-in an editor reads when its item vanished mid-edit (a stale click after a clear); setters no-op then. */
    private static final MenuItemSpec MISSING = new MenuItemSpec(
            new SlotSet(List.of(0)),
            0,
            "STONE",
            "",
            List.of(),
            new ItemDecor(1, Optional.empty(), false, List.of()),
            List.<Ref>of(),
            new ClickSpec(Map.of(), Map.of()),
            false,
            Optional.empty(),
            ItemType.NONE);

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final Messages messages;
    private final TextInput textInput;
    private final ListPropertyLayout loreLayout;
    private final MenuClickActionsView clickActions;
    private final MenuRequirementsView requirements;
    private final EntityEditorView<ItemTarget> view;

    /**
     * The item each viewer is currently editing, so the click-action and requirement sub-editors, which open their own
     * engine windows and cannot carry the subject back through the framework's {@code onBack}, can reopen this editor
     * for the right item via {@link #reopen}. Mirrors the grid's per-viewer session map.
     */
    private final Map<UUID, ItemTarget> openTargets = new ConcurrentHashMap<>();

    public MenuItemEditorView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            Messages messages,
            TextInput textInput,
            GuiLayouts guiLayouts,
            BiConsumer<Player, PlayerRef> onBackToGrid,
            MenuClickActionsView clickActions,
            MenuRequirementsView requirements) {
        Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.clickActions = Objects.requireNonNull(clickActions, "clickActions");
        this.requirements = Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(onBackToGrid, "onBackToGrid");
        this.loreLayout = defaultLoreLayout();
        EntityEditorLayout layout = guiLayouts.loadEntityEditor(MODULE, LAYOUT, codeDefault());
        this.view = EntityEditorView.<ItemTarget>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(this::title)
                .valueLore(CustomMenusMessageKey.MENU_ITEM_EDITOR_VALUE_LORE)
                .backName(CustomMenusMessageKey.MENU_ITEM_EDITOR_BACK)
                .properties(this::properties)
                .onBack(onBackToGrid)
                .build();
    }

    /** Open the property editor for the item under {@code itemId} in {@code session}, on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer, MenuEditSession session, String itemId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(itemId, "itemId");
        ItemTarget target = new ItemTarget(session, itemId);
        openTargets.put(viewer.uuid(), target);
        view.open(player, viewer, target);
    }

    /**
     * Reopen the item editor for whatever item {@code viewer} was last editing, the target the click-action and
     * requirement sub-editors return to, since they open their own windows and lose the framework's subject on back. A
     * no-op when the viewer has no tracked item (they never opened one, or it closed).
     */
    public void reopen(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        ItemTarget target = openTargets.get(viewer.uuid());
        if (target != null) {
            view.open(player, viewer, target);
        }
    }

    /** The property drawn at {@code slot} for the item, exposed so a test can resolve it without firing a click. */
    Optional<EditableProperty> propertyAt(int slot, MenuEditSession session, String itemId) {
        return view.propertyAt(slot, new ItemTarget(session, itemId));
    }

    private Component title(PlayerRef viewer, ItemTarget target) {
        int slot = current(target).slots().slots().stream().findFirst().orElse(0);
        return guiText.text(
                viewer,
                CustomMenusMessageKey.MENU_ITEM_EDITOR_TITLE,
                Map.of("id", target.itemId(), "slot", Integer.toString(slot)));
    }

    private List<EditableProperty> properties(ItemTarget target) {
        List<EditableProperty> props = new ArrayList<>();
        props.add(materialProperty(target));
        props.add(captureProperty(target));
        props.add(nameProperty(target));
        props.add(loreProperty(target));
        props.add(slotsProperty(target));
        props.add(amountProperty(target));
        props.add(priorityProperty(target));
        props.add(modelDataProperty(target));
        props.add(glowProperty(target));
        props.add(vanillaTooltipProperty(target));
        props.add(loreModeProperty(target));
        props.add(typeProperty(target));
        props.add(clickActions.row(target.session(), target.itemId()));
        props.add(requirements.row(target.session(), target.itemId()));
        for (String token : FLAG_TOKENS) {
            props.add(flagProperty(target, token));
        }
        return props;
    }

    // --- material ---------------------------------------------------------------------------------------------

    private EditableProperty materialProperty(ItemTarget target) {
        return new TextProperty(
                TEXT_INPUT_KEY,
                CustomMenusMessageKey.MENU_ITEM_EDITOR_MATERIAL,
                CustomMenusMessageKey.MENU_ITEM_EDITOR_MATERIAL_PROMPT,
                Material.STONE,
                () -> current(target).material(),
                raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw.strip()),
                value -> target.session().setMaterial(target.itemId(), value),
                textInput,
                scheduler);
    }

    private EditableProperty captureProperty(ItemTarget target) {
        return new ActionProperty(
                CustomMenusMessageKey.MENU_ITEM_EDITOR_CAPTURE,
                Material.CHEST,
                hint(CustomMenusMessageKey.MENU_ITEM_EDITOR_CAPTURE_HINT),
                (player, reopen) -> capture(player, target, reopen));
    }

    /** Copy the held item (all its NBT) into the slot as a {@code b64:} token, or tell the viewer their hand is empty. */
    private void capture(Player player, ItemTarget target, Runnable reopen) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(guiText.text(viewer, CustomMenusMessageKey.MENU_ITEM_EDITOR_CAPTURE_EMPTY));
            reopen.run();
            return;
        }
        String token = SerializedItems.encode(hand);
        scheduler.async(() -> {
            target.session().setMaterial(target.itemId(), token);
            scheduler.onEntity(viewer, () -> {
                player.sendMessage(guiText.text(viewer, CustomMenusMessageKey.MENU_ITEM_EDITOR_CAPTURED));
                reopen.run();
            });
        });
    }

    // --- name / lore / slots ----------------------------------------------------------------------------------

    private EditableProperty nameProperty(ItemTarget target) {
        return new TextProperty(
                TEXT_INPUT_KEY,
                CustomMenusMessageKey.MENU_ITEM_EDITOR_NAME,
                CustomMenusMessageKey.MENU_ITEM_EDITOR_NAME_PROMPT,
                Material.NAME_TAG,
                () -> current(target).name(),
                raw -> Optional.of(clearToken(raw) ? "" : raw),
                value -> target.session().setName(target.itemId(), value),
                textInput,
                scheduler);
    }

    private EditableProperty loreProperty(ItemTarget target) {
        return new ListProperty(
                LIST_INPUT_KEY,
                CustomMenusMessageKey.MENU_ITEM_EDITOR_LORE,
                Material.WRITABLE_BOOK,
                guiText,
                () -> current(target).lore(),
                lore -> target.session().setLore(target.itemId(), lore),
                new ListPropertyText(
                        CustomMenusMessageKey.MENU_ITEM_EDITOR_LORE_TITLE,
                        CustomMenusMessageKey.MENU_ITEM_EDITOR_LORE_ENTRY_NAME,
                        CustomMenusMessageKey.MENU_ITEM_EDITOR_LORE_ENTRY_HINTS,
                        CustomMenusMessageKey.MENU_ITEM_EDITOR_LORE_ADD,
                        CustomMenusMessageKey.MENU_ITEM_EDITOR_LORE_ADD_PROMPT,
                        CustomMenusMessageKey.MENU_ITEM_EDITOR_LORE_EDIT_PROMPT,
                        CustomMenusMessageKey.MENU_ITEM_EDITOR_LORE_REMOVE_CONFIRM,
                        CustomMenusMessageKey.MENU_ITEM_EDITOR_LORE_BACK),
                loreLayout,
                textInput,
                scheduler);
    }

    private EditableProperty slotsProperty(ItemTarget target) {
        return new TextProperty(
                TEXT_INPUT_KEY,
                CustomMenusMessageKey.MENU_ITEM_EDITOR_SLOTS,
                CustomMenusMessageKey.MENU_ITEM_EDITOR_SLOTS_PROMPT,
                Material.CRAFTING_TABLE,
                () -> slotToken(current(target).slots()),
                raw -> validateSlots(target, raw),
                value -> target.session().setSlots(target.itemId(), parseSlots(target, value)),
                textInput,
                scheduler);
    }

    private Optional<String> validateSlots(ItemTarget target, String raw) {
        try {
            parseSlots(target, raw);
            return Optional.of(raw.strip());
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    /** Parse a compact slot token ({@code "0-2,8"}) against the menu's capacity, so a slot past the last row is rejected. */
    private static SlotSet parseSlots(ItemTarget target, String raw) {
        int size = Math.max(1, target.session().rows() * 9);
        List<String> tokens = Splitter.on(',').trimResults().omitEmptyStrings().splitToList(raw);
        return SlotSet.parse(tokens, size);
    }

    private static String slotToken(SlotSet slots) {
        List<String> parts = new ArrayList<>();
        for (int slot : slots.slots()) {
            parts.add(Integer.toString(slot));
        }
        return String.join(",", parts);
    }

    // --- numbers ----------------------------------------------------------------------------------------------

    private EditableProperty amountProperty(ItemTarget target) {
        return new NumberProperty(
                CustomMenusMessageKey.MENU_ITEM_EDITOR_AMOUNT,
                Material.PAPER,
                () -> current(target).decor().amount(),
                1,
                8,
                1,
                64,
                value -> target.session().setAmount(target.itemId(), (int) (long) value),
                scheduler);
    }

    private EditableProperty priorityProperty(ItemTarget target) {
        return new NumberProperty(
                CustomMenusMessageKey.MENU_ITEM_EDITOR_PRIORITY,
                Material.COMPARATOR,
                () -> current(target).priority(),
                1,
                5,
                -64,
                64,
                value -> target.session().setPriority(target.itemId(), (int) (long) value),
                scheduler);
    }

    private EditableProperty modelDataProperty(ItemTarget target) {
        return new NumberProperty(
                CustomMenusMessageKey.MENU_ITEM_EDITOR_MODEL_DATA,
                Material.CLOCK,
                () -> current(target)
                        .decor()
                        .modelData()
                        .map(Integer::longValue)
                        .orElse(0L),
                1,
                100,
                0,
                MAX_MODEL_DATA,
                value -> target.session()
                        .setModelData(target.itemId(), value <= 0 ? Optional.empty() : Optional.of((int) (long) value)),
                scheduler);
    }

    // --- toggles / selectors ----------------------------------------------------------------------------------

    private EditableProperty glowProperty(ItemTarget target) {
        return ToggleProperty.ofBoolean(
                CustomMenusMessageKey.MENU_ITEM_EDITOR_GLOW,
                Material.GLOWSTONE_DUST,
                () -> current(target).decor().glow(),
                this::onOff,
                on -> target.session().setGlow(target.itemId(), on),
                scheduler);
    }

    /**
     * Whether the client writes its own lines under this icon's lore. On is the default, because a menu icon is a
     * button: an IRON_PICKAXE otherwise carries its mining speed and an armour piece says what it is worn as,
     * under a lore that already said what the button does. This replaced the HIDE_ADDITIONAL_TOOLTIP flag, which
     * Paper 26.2 deprecates and which never covered the dyed/equippable/trim/firework lines at all.
     */
    private EditableProperty vanillaTooltipProperty(ItemTarget target) {
        return ToggleProperty.ofBoolean(
                CustomMenusMessageKey.MENU_ITEM_EDITOR_HIDE_VANILLA_TOOLTIP,
                Material.PAPER,
                () -> current(target)
                        .decor()
                        .meta()
                        .components()
                        .hideVanillaTooltip()
                        .orElse(true),
                this::onOff,
                hidden -> target.session().setVanillaTooltipHidden(target.itemId(), hidden),
                scheduler);
    }

    private EditableProperty flagProperty(ItemTarget target, String token) {
        return ToggleProperty.ofBoolean(
                Objects.requireNonNull(FLAG_LABELS.get(token), "flag label"),
                Material.ITEM_FRAME,
                () -> current(target).decor().flagTokens().contains(token),
                this::onOff,
                on -> toggleFlag(target, token, on),
                scheduler);
    }

    private void toggleFlag(ItemTarget target, String token, boolean on) {
        List<String> next = new ArrayList<>(current(target).decor().flagTokens());
        next.remove(token);
        if (on) {
            next.add(token);
        }
        target.session().setFlags(target.itemId(), next);
    }

    private EditableProperty loreModeProperty(ItemTarget target) {
        return new EnumProperty<>(
                CustomMenusMessageKey.MENU_ITEM_EDITOR_LORE_MODE,
                CustomMenusMessageKey.MENU_ITEM_EDITOR_SELECT_LORE_MODE,
                Material.BOOK,
                guiText,
                List.of(LoreMode.values()),
                () -> current(target).loreMode(),
                (viewer, mode) -> mode.name(),
                mode -> target.session().setLoreMode(target.itemId(), mode),
                SELECTOR_OPTION_ICON,
                SELECTOR_FILLER,
                SELECTOR_SLOTS,
                SELECTOR_ROWS,
                scheduler);
    }

    private EditableProperty typeProperty(ItemTarget target) {
        return new EnumProperty<>(
                CustomMenusMessageKey.MENU_ITEM_EDITOR_TYPE,
                CustomMenusMessageKey.MENU_ITEM_EDITOR_SELECT_TYPE,
                Material.MAP,
                guiText,
                List.of(ItemType.values()),
                () -> current(target).type(),
                (viewer, type) -> type.name(),
                type -> target.session().setType(target.itemId(), type),
                SELECTOR_OPTION_ICON,
                SELECTOR_FILLER,
                SELECTOR_SLOTS,
                SELECTOR_ROWS,
                scheduler);
    }

    // --- helpers ----------------------------------------------------------------------------------------------

    private static MenuItemSpec current(ItemTarget target) {
        return target.session().item(target.itemId()).orElse(MISSING);
    }

    private String onOff(PlayerRef viewer, boolean on) {
        return messages.resolve(
                viewer,
                on ? CustomMenusMessageKey.MENU_ITEM_EDITOR_VALUE_ON : CustomMenusMessageKey.MENU_ITEM_EDITOR_VALUE_OFF,
                Map.of());
    }

    private Function<PlayerRef, String> hint(MessageKey key) {
        return viewer -> messages.resolve(viewer, key, Map.of());
    }

    /** Whether a typed line means "clear back to the base icon's own name", a dash, {@code none}, {@code clear}, or blank. */
    private static boolean clearToken(String raw) {
        String value = raw.strip();
        return value.isEmpty()
                || value.equals("-")
                || value.equalsIgnoreCase("none")
                || value.equalsIgnoreCase("clear");
    }

    private static final int SELECTOR_ROWS = 3;
    private static final List<Integer> SELECTOR_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16);
    private static final Material SELECTOR_OPTION_ICON = Material.PAPER;
    private static final Material SELECTOR_FILLER = Material.BLACK_STAINED_GLASS_PANE;

    private static EntityEditorLayout codeDefault() {
        return new EntityEditorLayout(
                6,
                PROPERTY_SLOTS,
                BACK_SLOT,
                OptionalInt.empty(),
                Material.ARROW,
                Material.BARRIER,
                Material.BLACK_STAINED_GLASS_PANE);
    }

    private static ListPropertyLayout defaultLoreLayout() {
        return new ListPropertyLayout(
                6,
                List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34),
                48,
                50,
                Material.PAPER,
                Material.LIME_DYE,
                Material.ARROW,
                Material.BLACK_STAINED_GLASS_PANE);
    }

    /** The subject an editor open carries: the grid's working copy and the id of the item being edited within it. */
    private record ItemTarget(MenuEditSession session, String itemId) {
        private ItemTarget {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(itemId, "itemId");
        }
    }
}
