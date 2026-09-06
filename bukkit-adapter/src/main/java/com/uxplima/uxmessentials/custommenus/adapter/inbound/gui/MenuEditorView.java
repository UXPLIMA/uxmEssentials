package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.custommenus.adapter.MenuEditLocks;
import com.uxplima.uxmessentials.custommenus.adapter.MenuEditorService;
import com.uxplima.uxmessentials.custommenus.adapter.MenuEditorService.EditOutcome;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ActionProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code /menu editor} menu picker: a paginated {@link EntityListView} of every loaded custom menu with a create
 * button, and a per-menu {@link EntityEditorView} overview carrying save / duplicate / rename / delete. It is a thin
 * consumer of the shared menu engine. The list opens through the façade, each button is an {@link ActionProperty} or
 * the editor's own back/delete, names are captured through {@link TextInput}, and the destructive delete gates behind
 * the engine's confirm, so no raw Bukkit inventory is ever built and the editor stays on the engine like every other
 * spec-driven menu.
 *
 * <p>All file work runs through the {@link MenuEditorService}, off the tick thread: a create / duplicate / rename /
 * delete is scheduled async, its typed outcome carried back to the viewer's entity thread to show a confirmation and
 * reopen the right window (the new menu's overview after a create, the list after a delete). The picker reads the live
 * loaded-name set fresh on each open, so a menu created or deleted here appears or disappears immediately. P1 wires the
 * menu-level CRUD only; the overview's slot-grid button is a placeholder the P2 grid editor fills in.
 */
@NullMarked
public final class MenuEditorView {

    private static final String MODULE = "custommenus";
    private static final String LIST_LAYOUT = "menu-editor-list";
    private static final String OVERVIEW_LAYOUT = "menu-editor";

    private static final String CREATE_INPUT_KEY = "custommenus.editor-create";
    private static final String DUPLICATE_INPUT_KEY = "custommenus.editor-duplicate";
    private static final String RENAME_INPUT_KEY = "custommenus.editor-rename";

    /** The create button's slot on the six-row picker. */
    private static final int CREATE_SLOT = 49;

    /** The overview's five action-button slots, then its back and delete slots, a single three-row chest. */
    private static final List<Integer> OVERVIEW_PROPERTY_SLOTS = List.of(10, 12, 14, 16, 18);

    private static final int OVERVIEW_BACK_SLOT = 22;
    private static final int OVERVIEW_DELETE_SLOT = 26;

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MenuEditorService service;
    private final MenuEditLocks locks;
    private final Function<String, Optional<MenuSpec>> specOf;
    private final TextInput textInput;
    private final BiConsumer<Player, String> openGrid;
    private final BiConsumer<Player, String> openProperties;
    private final EntityListView<String> list;
    private final EntityEditorView<String> overview;

    public MenuEditorView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            Messages messages,
            MenuEditorService service,
            MenuEditLocks locks,
            Supplier<List<String>> menuNames,
            Function<String, Optional<MenuSpec>> specOf,
            GuiLayouts guiLayouts,
            TextInput textInput,
            BiConsumer<Player, String> openGrid,
            BiConsumer<Player, String> openProperties) {
        Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.service = Objects.requireNonNull(service, "service");
        this.locks = Objects.requireNonNull(locks, "locks");
        Objects.requireNonNull(menuNames, "menuNames");
        this.specOf = Objects.requireNonNull(specOf, "specOf");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.openGrid = Objects.requireNonNull(openGrid, "openGrid");
        this.openProperties = Objects.requireNonNull(openProperties, "openProperties");
        Objects.requireNonNull(guiLayouts, "guiLayouts");

        EntityEditorLayout overviewLayout = guiLayouts.loadEntityEditor(
                MODULE,
                OVERVIEW_LAYOUT,
                EntityEditorLayout.withDelete(OVERVIEW_PROPERTY_SLOTS, OVERVIEW_BACK_SLOT, OVERVIEW_DELETE_SLOT));
        this.overview = EntityEditorView.<String>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(overviewLayout)
                .title((viewer, id) -> overviewTitle(viewer, id))
                .valueLore(CustomMenusMessageKey.MENU_EDITOR_OVERVIEW_VALUE_LORE)
                .backName(CustomMenusMessageKey.MENU_EDITOR_OVERVIEW_BACK)
                .properties(this::overviewProperties)
                // Routed through a method rather than a field-touching lambda: the list field is assigned after this
                // builder, so the back handler reads it at click time, not at construction.
                .onBack(this::openList)
                .onDelete(
                        CustomMenusMessageKey.MENU_EDITOR_DELETE,
                        CustomMenusMessageKey.MENU_EDITOR_DELETE_CONFIRM,
                        this::deleteMenu)
                .build();

        EntityListLayout listLayout = guiLayouts.loadEntityList(
                MODULE, LIST_LAYOUT, EntityListLayout.withCreate(Material.CHEST, CREATE_SLOT, Material.LIME_DYE));
        this.list = EntityListView.<String>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(listLayout)
                .title(CustomMenusMessageKey.MENU_EDITOR_TITLE)
                .emptyTitle(CustomMenusMessageKey.MENU_EDITOR_EMPTY_TITLE)
                .navNames(
                        com.uxplima.uxmessentials.shared.application.message.GuiMessageKey.PAGE_PREVIOUS,
                        com.uxplima.uxmessentials.shared.application.message.GuiMessageKey.PAGE_NEXT)
                .entities(menuNames)
                .iconRenderer(this::listIcon)
                .onSelect((player, id) -> overview.open(player, BukkitRefs.toRef(player), id))
                .onCreate(CustomMenusMessageKey.MENU_EDITOR_CREATE, this::promptCreate)
                .build();
    }

    /**
     * Open the menu picker for {@code player}; the framework schedules it on the viewer's entity thread. Arriving at
     * the browser means the viewer is no longer editing a specific menu, so their edit lock (if any) is released here
     * this is the "done with this menu" signal, alongside the quit hook.
     */
    public void open(Player player, PlayerRef viewer) {
        locks.release(viewer.uuid());
        list.open(player, viewer);
    }

    /** Reopen the picker: the overview's back target, releasing the viewer's edit lock as the browser is the root. */
    private void openList(Player player, PlayerRef viewer) {
        locks.release(viewer.uuid());
        list.open(player, viewer);
    }

    /** The picker list, exposed so a test can assert its rendered rows and the create handler. */
    public EntityListView<String> list() {
        return list;
    }

    /** The per-menu overview, exposed so a test can resolve a slot to its property without a live click. */
    public EntityEditorView<String> overview() {
        return overview;
    }

    // --- list icon / create ---

    private ItemStack listIcon(PlayerRef viewer, String id) {
        MenuSpec spec = specOf.apply(id).orElse(null);
        Map<String, String> placeholders = Map.of(
                "name",
                id,
                "title",
                plainTitle(spec),
                "rows",
                spec == null ? "0" : Integer.toString(spec.rows()),
                "items",
                spec == null ? "0" : Integer.toString(spec.items().size()));
        return ItemBuilder.of(Material.CHEST)
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        guiText.text(viewer, CustomMenusMessageKey.MENU_EDITOR_ENTRY_NAME, placeholders),
                        guiText.text(viewer, CustomMenusMessageKey.MENU_EDITOR_ENTRY_LORE, placeholders)))
                .build();
    }

    private void promptCreate(Player player) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        textInput.prompt(
                player,
                viewer,
                InputRequest.of(CREATE_INPUT_KEY, CustomMenusMessageKey.MENU_EDITOR_CREATE_PROMPT),
                text -> applyCreate(player, viewer, text),
                () -> list.open(player, viewer));
    }

    /** Create the named menu off-thread, then land in its property editor (or back in the list on a rejected name). */
    void applyCreate(Player player, PlayerRef viewer, String text) {
        if (text.isBlank()) {
            list.open(player, viewer);
            return;
        }
        String name = text.strip();
        scheduler.async(() -> {
            EditOutcome outcome = service.createBlank(name);
            scheduler.onEntity(viewer, () -> {
                feedback(player, viewer, outcome, name, name);
                // A create-from-scratch flows straight into the property editor so the operator sets its title, rows,
                // and open command right away; a rejected name lands back in the picker list.
                if (outcome == EditOutcome.CREATED) {
                    openProperties.accept(player, name);
                } else {
                    list.open(player, viewer);
                }
            });
        });
    }

    // --- overview buttons ---

    private net.kyori.adventure.text.Component overviewTitle(PlayerRef viewer, String id) {
        MenuSpec spec = specOf.apply(id).orElse(null);
        return guiText.text(
                viewer,
                CustomMenusMessageKey.MENU_EDITOR_OVERVIEW_TITLE,
                Map.of(
                        "name", id,
                        "rows", spec == null ? "0" : Integer.toString(spec.rows()),
                        "items",
                                spec == null
                                        ? "0"
                                        : Integer.toString(spec.items().size())));
    }

    private List<EditableProperty> overviewProperties(String id) {
        return List.of(saveButton(id), duplicateButton(id), renameButton(id), gridButton(id), propertiesButton(id));
    }

    private EditableProperty saveButton(String id) {
        return new ActionProperty(
                CustomMenusMessageKey.MENU_EDITOR_SAVE,
                Material.EMERALD,
                hint(CustomMenusMessageKey.MENU_EDITOR_SAVE_HINT),
                (player, reopen) -> saveMenu(player, id, reopen));
    }

    private EditableProperty duplicateButton(String id) {
        return new ActionProperty(
                CustomMenusMessageKey.MENU_EDITOR_DUPLICATE,
                Material.BOOK,
                hint(CustomMenusMessageKey.MENU_EDITOR_DUPLICATE_HINT),
                (player, reopen) -> promptDuplicate(player, id, reopen));
    }

    private EditableProperty renameButton(String id) {
        return new ActionProperty(
                CustomMenusMessageKey.MENU_EDITOR_RENAME,
                Material.NAME_TAG,
                hint(CustomMenusMessageKey.MENU_EDITOR_RENAME_HINT),
                (player, reopen) -> promptRename(player, id, reopen));
    }

    /** The overview button that opens the slot-grid canvas for this menu: the P2 editor's entry point. */
    private EditableProperty gridButton(String id) {
        return new ActionProperty(
                CustomMenusMessageKey.MENU_EDITOR_GRID,
                Material.CRAFTING_TABLE,
                hint(CustomMenusMessageKey.MENU_EDITOR_GRID_HINT),
                (player, reopen) -> openGrid.accept(player, id));
    }

    /** The overview button that opens the menu-level property editor for this menu: the P5 editor's entry point. */
    private EditableProperty propertiesButton(String id) {
        return new ActionProperty(
                CustomMenusMessageKey.MENU_EDITOR_PROPERTIES,
                Material.COMPARATOR,
                hint(CustomMenusMessageKey.MENU_EDITOR_PROPERTIES_HINT),
                (player, reopen) -> openProperties.accept(player, id));
    }

    private void saveMenu(Player player, String id, Runnable reopen) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        scheduler.async(() -> {
            EditOutcome outcome = service.save(id);
            scheduler.onEntity(viewer, () -> {
                feedback(player, viewer, outcome, id, id);
                reopen.run();
            });
        });
    }

    private void promptDuplicate(Player player, String id, Runnable reopen) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        textInput.prompt(
                player,
                viewer,
                InputRequest.of(DUPLICATE_INPUT_KEY, CustomMenusMessageKey.MENU_EDITOR_DUPLICATE_PROMPT, id(id)),
                text -> applyDuplicate(player, viewer, id, text),
                reopen);
    }

    /** Copy the menu off-thread, then land in the copy's overview (or the source's on a rejected name). */
    void applyDuplicate(Player player, PlayerRef viewer, String from, String text) {
        if (text.isBlank()) {
            overview.open(player, viewer, from);
            return;
        }
        String to = text.strip();
        scheduler.async(() -> {
            EditOutcome outcome = service.duplicate(from, to);
            scheduler.onEntity(viewer, () -> {
                feedback(player, viewer, outcome, from, to);
                overview.open(player, viewer, outcome == EditOutcome.DUPLICATED ? to : from);
            });
        });
    }

    private void promptRename(Player player, String id, Runnable reopen) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        textInput.prompt(
                player,
                viewer,
                InputRequest.of(RENAME_INPUT_KEY, CustomMenusMessageKey.MENU_EDITOR_RENAME_PROMPT, id(id)),
                text -> applyRename(player, viewer, id, text),
                reopen);
    }

    /** Rename the menu off-thread, then land in the renamed menu's overview (or the source's on a rejected name). */
    void applyRename(Player player, PlayerRef viewer, String from, String text) {
        if (text.isBlank()) {
            overview.open(player, viewer, from);
            return;
        }
        String to = text.strip();
        scheduler.async(() -> {
            EditOutcome outcome = service.rename(from, to);
            scheduler.onEntity(viewer, () -> {
                feedback(player, viewer, outcome, from, to);
                if (outcome == EditOutcome.RENAMED) {
                    overview.open(player, viewer, to);
                } else {
                    overview.open(player, viewer, from);
                }
            });
        });
    }

    /** The overview's delete button (confirm-gated): delete off-thread, then land back in the picker list. */
    private void deleteMenu(Player player, String id) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        scheduler.async(() -> {
            EditOutcome outcome = service.delete(id);
            scheduler.onEntity(viewer, () -> {
                feedback(player, viewer, outcome, id, id);
                list.open(player, viewer);
            });
        });
    }

    // --- feedback ---

    private void feedback(Player player, PlayerRef viewer, EditOutcome outcome, String from, String to) {
        Map<String, String> placeholders = Map.of("name", to, "from", from, "to", to, "missing", "");
        player.sendMessage(guiText.text(viewer, messageKey(outcome), placeholders));
    }

    private static MessageKey messageKey(EditOutcome outcome) {
        return switch (outcome) {
            case CREATED -> CustomMenusMessageKey.MENU_EDITOR_CREATED;
            case DUPLICATED -> CustomMenusMessageKey.MENU_EDITOR_DUPLICATED;
            case RENAMED -> CustomMenusMessageKey.MENU_EDITOR_RENAMED;
            case DELETED -> CustomMenusMessageKey.MENU_EDITOR_DELETED;
            case SAVED -> CustomMenusMessageKey.MENU_SAVED;
            case NAME_INVALID -> CustomMenusMessageKey.MENU_EDITOR_NAME_INVALID;
            case NAME_RESERVED -> CustomMenusMessageKey.MENU_EDITOR_NAME_RESERVED;
            case NAME_TAKEN -> CustomMenusMessageKey.MENU_EDITOR_NAME_TAKEN;
            case SOURCE_MISSING -> CustomMenusMessageKey.MENU_NOT_FOUND;
            case INVALID_REFS -> CustomMenusMessageKey.MENU_SAVE_INVALID;
            case IO_ERROR -> CustomMenusMessageKey.MENU_SAVE_FAILED;
        };
    }

    /** A fixed value-hint resolved in the viewer's locale: an action button's lore is not an editable value. */
    private Function<PlayerRef, String> hint(MessageKey key) {
        return viewer -> messages.resolve(viewer, key, Map.of());
    }

    private static Map<String, String> id(String id) {
        return Map.of("name", id);
    }

    /** The menu's title reduced to plain text (MiniMessage tags stripped), or empty when the menu is not registered. */
    private static String plainTitle(@org.jspecify.annotations.Nullable MenuSpec spec) {
        return spec == null ? "" : MiniMessage.miniMessage().stripTags(spec.title());
    }
}
