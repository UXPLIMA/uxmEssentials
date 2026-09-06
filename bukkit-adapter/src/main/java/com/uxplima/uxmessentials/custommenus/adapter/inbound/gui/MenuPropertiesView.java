package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.custommenus.adapter.MenuEditLocks;
import com.uxplima.uxmessentials.custommenus.adapter.MenuEditorService;
import com.uxplima.uxmessentials.custommenus.adapter.MenuEditorService.EditOutcome;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.OpenCommandSpec;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuEditSession;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuSchema;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ActionProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EnumProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.NumberProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.TextProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ToggleProperty;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The menu-level property editor of the {@code /menu editor}: opened from the overview's "Edit properties" button (and
 * landed on straight after a create), it edits everything about a menu that is not a slot, its title, row count,
 * inventory shape, click cooldown, chest-only / bottom-inventory flags, open requirement, open / close actions, refresh
 * policy, and its {@code command {}} open-command block. It is a thin consumer of the shared {@link EntityEditorView}
 * each field is one {@link EditableProperty} wired to a {@link MenuEditSession} setter, the ref lists reuse the P4
 * {@link MenuRefListEditor}, the requirement / command sub-editors open as engine child windows, so no raw Bukkit
 * inventory is built here and the editor stays on the menu engine like every other surface.
 *
 * <p>An open clones the registered {@link MenuSpec} (and its open command) into a per-viewer {@link MenuEditSession};
 * every field reads that working copy fresh on each draw and writes back through its setters, and the live menu changes
 * only on Save (the P0 validate → write → hot-reload path, off the tick thread). Shrinking the row count can orphan
 * items in now-out-of-range slots: {@link MenuEditSession#setRows} drops those items so the working copy stays valid
 * and Save never fails on them; the smaller item count shows on the redraw. Back returns to the overview, Delete gates
 * behind the engine confirm and returns to the picker list.
 */
@NullMarked
public final class MenuPropertiesView {

    private static final String MODULE = "custommenus";
    private static final String LAYOUT = "menu-properties-editor";
    private static final String TEXT_INPUT_KEY = "editor.text-field";

    /** The property-button slots, in the order {@link #properties} builds them; a six-row chest with room to spare. */
    private static final List<Integer> PROPERTY_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25);

    private static final int BACK_SLOT = 49;
    private static final int DELETE_SLOT = 53;

    private static final long MAX_COOLDOWN_MS = 60_000L;
    private static final long MAX_REFRESH_TICKS = 72_000L;

    private static final int SELECTOR_ROWS = 3;
    private static final List<Integer> SELECTOR_SLOTS = List.of(11, 12, 13, 14, 15);
    private static final Material SELECTOR_OPTION_ICON = Material.PAPER;
    private static final Material SELECTOR_FILLER = Material.BLACK_STAINED_GLASS_PANE;

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MenuEditorService service;
    private final MenuEditLocks locks;
    private final Function<String, Optional<MenuSpec>> specOf;
    private final Function<String, Optional<OpenCommandSpec>> openCommandFor;
    private final BiConsumer<String, Optional<OpenCommandSpec>> rememberCommand;
    private final MenuRefListEditor refList;
    private final Supplier<MenuSchema> schema;
    private final MenuCommandEditorView commandEditor;
    private final TextInput textInput;
    private final BiConsumer<Player, String> openGrid;
    private final BiConsumer<Player, String> openOverview;
    private final BiConsumer<Player, PlayerRef> openList;
    private final EntityEditorView<MenuTarget> view;

    /** The menu each viewer is editing, so the command sub-editor, its own engine window, can reopen the right one. */
    private final Map<UUID, MenuTarget> openTargets = new ConcurrentHashMap<>();

    public MenuPropertiesView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            Messages messages,
            MenuEditorService service,
            MenuEditLocks locks,
            Function<String, Optional<MenuSpec>> specOf,
            Function<String, Optional<OpenCommandSpec>> openCommandFor,
            BiConsumer<String, Optional<OpenCommandSpec>> rememberCommand,
            GuiLayouts guiLayouts,
            Supplier<MenuSchema> schema,
            MenuRefListEditor refList,
            MenuCommandEditorView commandEditor,
            TextInput textInput,
            BiConsumer<Player, String> openGrid,
            BiConsumer<Player, String> openOverview,
            BiConsumer<Player, PlayerRef> openList) {
        Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.service = Objects.requireNonNull(service, "service");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.specOf = Objects.requireNonNull(specOf, "specOf");
        this.openCommandFor = Objects.requireNonNull(openCommandFor, "openCommandFor");
        this.rememberCommand = Objects.requireNonNull(rememberCommand, "rememberCommand");
        this.refList = Objects.requireNonNull(refList, "refList");
        this.schema = Objects.requireNonNull(schema, "schema");
        this.commandEditor = Objects.requireNonNull(commandEditor, "commandEditor");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.openGrid = Objects.requireNonNull(openGrid, "openGrid");
        this.openOverview = Objects.requireNonNull(openOverview, "openOverview");
        this.openList = Objects.requireNonNull(openList, "openList");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        EntityEditorLayout layout = guiLayouts.loadEntityEditor(MODULE, LAYOUT, codeDefault());
        this.view = EntityEditorView.<MenuTarget>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(this::title)
                .valueLore(CustomMenusMessageKey.MENU_PROPERTIES_VALUE_LORE)
                .backName(CustomMenusMessageKey.MENU_PROPERTIES_BACK)
                .properties(this::properties)
                .onBack(this::backToOverview)
                .onDelete(
                        CustomMenusMessageKey.MENU_PROPERTIES_DELETE,
                        CustomMenusMessageKey.MENU_PROPERTIES_DELETE_CONFIRM,
                        this::deleteMenu)
                .build();
    }

    /**
     * Open the menu-property editor for {@code menuId}: take the menu's edit lock, clone the registered spec (and its
     * open command) into a fresh per-viewer working copy, then show the editor. A menu no longer registered tells the
     * viewer and returns to the picker list; a menu another operator already has open is refused with a "being edited
     * by …" line, so two sessions can never save over each other.
     */
    public void open(Player player, PlayerRef viewer, String menuId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(menuId, "menuId");
        MenuSpec spec = specOf.apply(menuId).orElse(null);
        if (spec == null) {
            player.sendMessage(guiText.text(viewer, CustomMenusMessageKey.MENU_NOT_FOUND, Map.of("name", menuId)));
            openList.accept(player, viewer);
            return;
        }
        Optional<String> lockedBy = locks.tryAcquire(menuId, viewer.uuid(), viewer.name());
        if (lockedBy.isPresent()) {
            player.sendMessage(
                    guiText.text(viewer, CustomMenusMessageKey.MENU_EDITOR_LOCKED, Map.of("player", lockedBy.get())));
            return;
        }
        MenuTarget target = new MenuTarget(
                menuId, MenuEditSession.from(spec, openCommandFor.apply(menuId).orElse(null)));
        openTargets.put(viewer.uuid(), target);
        view.open(player, viewer, target);
    }

    /** Reopen the property editor for whatever menu {@code viewer} was editing: the command sub-editor's back target. */
    public void reopen(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        MenuTarget target = openTargets.get(viewer.uuid());
        if (target != null) {
            view.open(player, viewer, target);
        }
    }

    /** The property drawn at {@code slot}, exposed so a test can resolve it without firing a click. */
    Optional<EditableProperty> propertyAt(int slot, MenuEditSession session, String menuId) {
        return view.propertyAt(slot, new MenuTarget(menuId, session));
    }

    /** The working copy a viewer is editing, exposed so a test can read it without a live save. */
    Optional<MenuEditSession> editSession(UUID viewer) {
        return Optional.ofNullable(openTargets.get(viewer)).map(MenuTarget::session);
    }

    private Component title(PlayerRef viewer, MenuTarget target) {
        return guiText.text(viewer, CustomMenusMessageKey.MENU_PROPERTIES_TITLE, Map.of("name", target.menuId()));
    }

    private List<EditableProperty> properties(MenuTarget target) {
        return List.of(
                titleRow(target),
                rowsRow(target),
                inventoryTypeRow(target),
                clickCooldownRow(target),
                chestOnlyRow(target),
                bottomInventoryRow(target),
                openRequirementRow(target),
                openActionsRow(target),
                closeActionsRow(target),
                refreshRow(target),
                refreshIntervalRow(target),
                openCommandRow(target),
                gridRow(target),
                saveRow(target));
    }

    // --- text / number / toggle / enum fields ---------------------------------------------------------------------

    private EditableProperty titleRow(MenuTarget target) {
        return new TextProperty(
                TEXT_INPUT_KEY,
                CustomMenusMessageKey.MENU_PROPERTIES_TITLE_FIELD,
                CustomMenusMessageKey.MENU_PROPERTIES_TITLE_PROMPT,
                Material.NAME_TAG,
                () -> target.session().title(),
                raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw),
                value -> target.session().setTitle(value),
                textInput,
                scheduler);
    }

    private EditableProperty rowsRow(MenuTarget target) {
        return new NumberProperty(
                CustomMenusMessageKey.MENU_PROPERTIES_ROWS,
                Material.LADDER,
                () -> target.session().rows(),
                1,
                1,
                1,
                6,
                value -> target.session().setRows((int) (long) value),
                scheduler);
    }

    private EditableProperty inventoryTypeRow(MenuTarget target) {
        return new EnumProperty<>(
                CustomMenusMessageKey.MENU_PROPERTIES_INVENTORY_TYPE,
                CustomMenusMessageKey.MENU_PROPERTIES_SELECT_INVENTORY_TYPE,
                Material.HOPPER,
                guiText,
                List.of(InventoryShape.values()),
                () -> InventoryShape.fromToken(target.session().inventoryType().orElse(null)),
                (viewer, shape) -> shape.name(),
                shape -> target.session().setInventoryType(shape.token()),
                SELECTOR_OPTION_ICON,
                SELECTOR_FILLER,
                SELECTOR_SLOTS,
                SELECTOR_ROWS,
                scheduler);
    }

    private EditableProperty clickCooldownRow(MenuTarget target) {
        return new NumberProperty(
                CustomMenusMessageKey.MENU_PROPERTIES_CLICK_COOLDOWN,
                Material.CLOCK,
                () -> target.session().clickCooldownMs(),
                50,
                10,
                0,
                MAX_COOLDOWN_MS,
                value -> target.session().setClickCooldownMs(value),
                scheduler);
    }

    private EditableProperty chestOnlyRow(MenuTarget target) {
        return ToggleProperty.ofBoolean(
                CustomMenusMessageKey.MENU_PROPERTIES_CHEST_ONLY,
                Material.CHEST,
                () -> target.session().chestOnly(),
                this::onOff,
                on -> target.session().setChestOnly(on),
                scheduler);
    }

    private EditableProperty bottomInventoryRow(MenuTarget target) {
        return ToggleProperty.ofBoolean(
                CustomMenusMessageKey.MENU_PROPERTIES_BOTTOM_INVENTORY,
                Material.LEATHER_CHESTPLATE,
                () -> target.session().bottomInventory(),
                this::onOff,
                on -> target.session().setBottomInventory(on),
                scheduler);
    }

    private EditableProperty refreshRow(MenuTarget target) {
        return ToggleProperty.ofBoolean(
                CustomMenusMessageKey.MENU_PROPERTIES_REFRESH,
                Material.REPEATER,
                () -> target.session().refresh().enabled(),
                this::onOff,
                on -> target.session().setRefresh(on, target.session().refresh().intervalTicks()),
                scheduler);
    }

    private EditableProperty refreshIntervalRow(MenuTarget target) {
        return new NumberProperty(
                CustomMenusMessageKey.MENU_PROPERTIES_REFRESH_INTERVAL,
                Material.CLOCK,
                () -> target.session().refresh().intervalTicks(),
                20,
                5,
                1,
                MAX_REFRESH_TICKS,
                value -> target.session().setRefresh(target.session().refresh().enabled(), (int) (long) value),
                scheduler);
    }

    // --- ref-list / sub-editor openers ----------------------------------------------------------------------------

    private EditableProperty openRequirementRow(MenuTarget target) {
        return new MenuOpenerProperty(
                CustomMenusMessageKey.MENU_PROPERTIES_OPEN_REQUIREMENT,
                Material.COMPARATOR,
                viewer -> Integer.toString(target.session().openRequirement().size()),
                context -> openRefList(
                        context,
                        CustomMenusMessageKey.MENU_PROPERTIES_OPEN_REQUIREMENT_TITLE,
                        schema.get().conditionIds(),
                        () -> target.session().openRequirement(),
                        refs -> target.session().setOpenRequirement(refs)));
    }

    private EditableProperty openActionsRow(MenuTarget target) {
        return new MenuOpenerProperty(
                CustomMenusMessageKey.MENU_PROPERTIES_OPEN_ACTIONS,
                Material.LEVER,
                viewer -> Integer.toString(target.session().openActions().size()),
                context -> openRefList(
                        context,
                        CustomMenusMessageKey.MENU_PROPERTIES_OPEN_ACTIONS_TITLE,
                        schema.get().actionIds(),
                        () -> target.session().openActions(),
                        refs -> target.session().setOpenActions(refs)));
    }

    private EditableProperty closeActionsRow(MenuTarget target) {
        return new MenuOpenerProperty(
                CustomMenusMessageKey.MENU_PROPERTIES_CLOSE_ACTIONS,
                Material.TRIPWIRE_HOOK,
                viewer -> Integer.toString(target.session().closeActions().size()),
                context -> openRefList(
                        context,
                        CustomMenusMessageKey.MENU_PROPERTIES_CLOSE_ACTIONS_TITLE,
                        schema.get().actionIds(),
                        () -> target.session().closeActions(),
                        refs -> target.session().setCloseActions(refs)));
    }

    private void openRefList(
            ClickContext context,
            MessageKey title,
            List<String> catalogIds,
            Supplier<List<Ref>> current,
            Consumer<List<Ref>> setter) {
        MenuRefListEditor.RefList list =
                new MenuRefListEditor.RefList(title, Map.of(), catalogIds, current, setter, context.reopen());
        refList.open(context, list);
    }

    private EditableProperty openCommandRow(MenuTarget target) {
        return new MenuOpenerProperty(
                CustomMenusMessageKey.MENU_PROPERTIES_OPEN_COMMAND,
                Material.COMMAND_BLOCK,
                viewer -> target.session().command().map(OpenCommandSpec::name).orElse(""),
                context -> commandEditor.open(context.player(), context.viewer(), target.session(), target.menuId()));
    }

    // --- action buttons -------------------------------------------------------------------------------------------

    private EditableProperty gridRow(MenuTarget target) {
        return new ActionProperty(
                CustomMenusMessageKey.MENU_PROPERTIES_GRID,
                Material.CRAFTING_TABLE,
                hint(CustomMenusMessageKey.MENU_PROPERTIES_GRID_HINT),
                (player, reopen) -> openGrid.accept(player, target.menuId()));
    }

    private EditableProperty saveRow(MenuTarget target) {
        return new ActionProperty(
                CustomMenusMessageKey.MENU_PROPERTIES_SAVE,
                Material.EMERALD,
                hint(CustomMenusMessageKey.MENU_PROPERTIES_SAVE_HINT),
                (player, reopen) -> saveMenu(player, target, reopen));
    }

    private void saveMenu(Player player, MenuTarget target, Runnable reopen) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        Optional<OpenCommandSpec> command = target.session().command();
        scheduler.async(() -> {
            EditOutcome outcome = service.saveSession(target.menuId(), target.session(), command.orElse(null));
            scheduler.onEntity(viewer, () -> {
                if (outcome == EditOutcome.SAVED) {
                    // Keep the live open-command reference in step so a later grid save does not revert this edit.
                    rememberCommand.accept(target.menuId(), command);
                }
                player.sendMessage(guiText.text(viewer, saveKey(outcome), name(target)));
                reopen.run();
            });
        });
    }

    private void deleteMenu(Player player, MenuTarget target) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        scheduler.async(() -> {
            EditOutcome outcome = service.delete(target.menuId());
            scheduler.onEntity(viewer, () -> {
                player.sendMessage(guiText.text(viewer, deleteKey(outcome), name(target)));
                openTargets.remove(viewer.uuid());
                openList.accept(player, viewer);
            });
        });
    }

    private void backToOverview(Player player, PlayerRef viewer) {
        MenuTarget target = openTargets.get(viewer.uuid());
        if (target != null) {
            openOverview.accept(player, target.menuId());
        } else {
            openList.accept(player, viewer);
        }
    }

    // --- helpers --------------------------------------------------------------------------------------------------

    private static MessageKey saveKey(EditOutcome outcome) {
        return switch (outcome) {
            case SAVED -> CustomMenusMessageKey.MENU_SAVED;
            case INVALID_REFS -> CustomMenusMessageKey.MENU_SAVE_INVALID;
            default -> CustomMenusMessageKey.MENU_SAVE_FAILED;
        };
    }

    private static MessageKey deleteKey(EditOutcome outcome) {
        return switch (outcome) {
            case DELETED -> CustomMenusMessageKey.MENU_EDITOR_DELETED;
            case SOURCE_MISSING -> CustomMenusMessageKey.MENU_NOT_FOUND;
            default -> CustomMenusMessageKey.MENU_SAVE_FAILED;
        };
    }

    private static Map<String, String> name(MenuTarget target) {
        return Map.of("name", target.menuId(), "missing", "");
    }

    private String onOff(PlayerRef viewer, boolean on) {
        return messages.resolve(
                viewer,
                on ? CustomMenusMessageKey.MENU_PROPERTIES_VALUE_ON : CustomMenusMessageKey.MENU_PROPERTIES_VALUE_OFF,
                Map.of());
    }

    private Function<PlayerRef, String> hint(MessageKey key) {
        return viewer -> messages.resolve(viewer, key, Map.of());
    }

    private static EntityEditorLayout codeDefault() {
        return new EntityEditorLayout(
                6,
                PROPERTY_SLOTS,
                BACK_SLOT,
                OptionalInt.of(DELETE_SLOT),
                Material.ARROW,
                Material.BARRIER,
                Material.BLACK_STAINED_GLASS_PANE);
    }

    /**
     * The chest-and-variants inventory shapes the editor offers. {@code CHEST} maps to the absent token (a plain
     * rows-based chest); the others carry the operator token the menu spec stores, so a round-trip is faithful.
     */
    private enum InventoryShape {
        CHEST(null),
        HOPPER("hopper"),
        DROPPER("dropper"),
        DISPENSER("dispenser");

        private final @Nullable String token;

        InventoryShape(@Nullable String token) {
            this.token = token;
        }

        @Nullable String token() {
            return token;
        }

        static InventoryShape fromToken(@Nullable String token) {
            if (token == null) {
                return CHEST;
            }
            for (InventoryShape shape : values()) {
                if (token.equalsIgnoreCase(shape.token)) {
                    return shape;
                }
            }
            return CHEST;
        }
    }

    /** The subject a property-editor open carries: the menu id and its per-viewer working copy. */
    private record MenuTarget(String menuId, MenuEditSession session) {
        private MenuTarget {
            Objects.requireNonNull(menuId, "menuId");
            Objects.requireNonNull(session, "session");
        }
    }
}
