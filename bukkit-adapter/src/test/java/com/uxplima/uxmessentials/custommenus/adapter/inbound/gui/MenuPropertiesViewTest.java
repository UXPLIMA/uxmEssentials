package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.custommenus.adapter.CustomMenuLoader;
import com.uxplima.uxmessentials.custommenus.adapter.MenuEditLocks;
import com.uxplima.uxmessentials.custommenus.adapter.MenuEditorService;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuEditSession;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecPersistence;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecWriter;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EnumProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.NumberProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.TextProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ToggleProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /menu editor} menu-property editor: opening it renders a holder-backed engine
 * editor whose rows are the menu-level fields, a {@link TextProperty} edits the title, a {@link NumberProperty} resizes
 * the menu, a {@link ToggleProperty} flips chest-only, the ref-list editor appends an open action, and the command
 * sub-editor sets an open-command name: all mutating the per-viewer working copy, with Save writing the reloaded menu.
 * Everything is an engine window, so the editor never touches a raw Bukkit inventory.
 */
class MenuPropertiesViewTest {

    private static final int TITLE_SLOT = 10; // PROPERTY_SLOTS[0]
    private static final int ROWS_SLOT = 11; // PROPERTY_SLOTS[1]
    private static final int INVENTORY_TYPE_SLOT = 12; // PROPERTY_SLOTS[2]
    private static final int CHEST_ONLY_SLOT = 14; // PROPERTY_SLOTS[4]
    private static final int OPEN_REQUIREMENT_SLOT = 16; // PROPERTY_SLOTS[6]
    private static final int OPEN_COMMAND_SLOT = 23; // PROPERTY_SLOTS[11]
    private static final int GRID_SLOT = 24; // PROPERTY_SLOTS[12]
    private static final int SAVE_SLOT = 25; // PROPERTY_SLOTS[13]
    private static final int EDITOR_BACK_SLOT = 49;
    private static final int EDITOR_DELETE_SLOT = 53;
    private static final int COMMAND_NAME_SLOT = 11; // MenuCommandEditorView.PROPERTY_SLOTS[1]

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private Menus menus;
    private CustomMenuLoader loader;
    private final List<String> names = new ArrayList<>();
    private MenuPropertiesView properties;
    private MenuCommandEditorView commandEditor;
    private MenuRefListEditor refListEditor;

    @TempDir
    Path menusDir;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        GuiText guiText = new GuiText(new KeyMessages());
        Scheduler scheduler = new SyncScheduler();

        MenuBindings bindings = new MenuBindings();
        bindings.action("close", ctx -> {});
        bindings.action("message", ctx -> {});
        bindings.action("sound", ctx -> {});
        bindings.condition("perm", (ctx, args) -> true);
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        MenuRenderer renderer =
                new MenuRenderer(new ItemRenderer(guiText, bindings.placeholders()), bindings.conditions());
        menus = new Menus(renderer, scheduler, bindings.lists(), editorRenderer);
        MenuListener listener = new MenuListener(
                renderer,
                bindings.actions(),
                bindings.conditions(),
                scheduler,
                plugin,
                editorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener());
        server.getPluginManager().registerEvents(listener, plugin);

        loader = new CustomMenuLoader(new MenuSpecLoader(), bindings, menus, NOOP);
        writeMenu("alpha", 3, "x { slot = 0, material = STONE, click { left = [\"close\"] } }");
        writeMenu("big", 6, "y { slot = 45, material = DIRT, click { left = [\"close\"] } }");
        names.addAll(loader.loadFrom(menusDir).loadedNames());

        MenuSpecPersistence persistence = new MenuSpecPersistence(new MenuSpecWriter(), bindings, NOOP);
        MenuEditorService service = new MenuEditorService(
                menusDir,
                persistence,
                menus::registeredSpec,
                name -> Optional.empty(),
                this::reloadOne,
                this::forget,
                NOOP);
        TextInput textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);
        GuiLayouts guiLayouts = new GuiLayouts(menusDir, NOOP);
        refListEditor = new MenuRefListEditor(guiText, scheduler, textInput, "menu.action-editor.arg");
        commandEditor = new MenuCommandEditorView(
                menus, guiText, scheduler, new KeyMessages(), textInput, guiLayouts, (p, v) -> {});
        properties = new MenuPropertiesView(
                menus,
                guiText,
                scheduler,
                new KeyMessages(),
                service,
                new MenuEditLocks(),
                menus::registeredSpec,
                name -> Optional.empty(),
                (name, command) -> {},
                guiLayouts,
                bindings::schema,
                refListEditor,
                commandEditor,
                textInput,
                (p, id) -> {},
                (p, id) -> {},
                (p, v) -> {});
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theEditorOpensWithItsPropertyRows() {
        properties.open(player, viewer, "alpha");

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getSize()).isEqualTo(54); // the six-row property editor
        assertThat(inv.getItem(TITLE_SLOT).getType()).isEqualTo(Material.NAME_TAG); // title field
        assertThat(inv.getItem(ROWS_SLOT).getType()).isEqualTo(Material.LADDER); // rows field
        assertThat(inv.getItem(CHEST_ONLY_SLOT).getType()).isEqualTo(Material.CHEST); // chest-only toggle
        assertThat(inv.getItem(GRID_SLOT).getType()).isEqualTo(Material.CRAFTING_TABLE); // grid button
        assertThat(inv.getItem(SAVE_SLOT).getType()).isEqualTo(Material.EMERALD); // save button
        assertThat(inv.getItem(EDITOR_BACK_SLOT).getType()).isEqualTo(Material.ARROW); // back
        assertThat(inv.getItem(EDITOR_DELETE_SLOT).getType()).isEqualTo(Material.BARRIER); // delete
    }

    @Test
    void theRowsResolveToTheExpectedPropertyTypes() {
        properties.open(player, viewer, "alpha");
        MenuEditSession session = session();

        assertThat(properties.propertyAt(TITLE_SLOT, session, "alpha")).get().isInstanceOf(TextProperty.class);
        assertThat(properties.propertyAt(ROWS_SLOT, session, "alpha")).get().isInstanceOf(NumberProperty.class);
        assertThat(properties.propertyAt(INVENTORY_TYPE_SLOT, session, "alpha"))
                .get()
                .isInstanceOf(EnumProperty.class);
        assertThat(properties.propertyAt(CHEST_ONLY_SLOT, session, "alpha"))
                .get()
                .isInstanceOf(ToggleProperty.class);
        assertThat(properties.propertyAt(OPEN_REQUIREMENT_SLOT, session, "alpha"))
                .get()
                .isInstanceOf(MenuOpenerProperty.class);
        assertThat(properties.propertyAt(OPEN_COMMAND_SLOT, session, "alpha"))
                .get()
                .isInstanceOf(MenuOpenerProperty.class);
    }

    @Test
    void editingTheTitleMutatesTheSession() {
        properties.open(player, viewer, "alpha");
        MenuEditSession session = session();
        TextProperty title = (TextProperty)
                properties.propertyAt(TITLE_SLOT, session, "alpha").orElseThrow();

        title.applyInput(context(), "<red>New title");

        assertThat(session.title()).isEqualTo("<red>New title");
    }

    @Test
    void theRowsStepperResizesTheSessionAndDropsOrphanedItems() {
        properties.open(player, viewer, "big"); // six rows, an item at slot 45
        MenuEditSession session = session();

        fireClick(ROWS_SLOT, ClickType.RIGHT); // step rows down: 6 -> 5, capacity 45, slot 45 no longer fits

        assertThat(session.rows()).isEqualTo(5);
        assertThat(session.item("y")).isEmpty();
        assertThatCode(session::toSpec).doesNotThrowAnyException();
    }

    @Test
    void togglingChestOnlyMutatesTheSession() {
        properties.open(player, viewer, "alpha");
        MenuEditSession session = session();

        fireClick(CHEST_ONLY_SLOT, ClickType.LEFT);

        assertThat(session.chestOnly()).isTrue();
    }

    @Test
    void addingAnOpenActionThroughTheRefEditorMutatesTheSession() {
        properties.open(player, viewer, "alpha");
        MenuEditSession session = session();
        MenuRefListEditor.RefList list = new MenuRefListEditor.RefList(
                CustomMenusMessageKey.MENU_PROPERTIES_OPEN_ACTIONS_TITLE,
                Map.of(),
                List.of("close", "message", "sound"),
                session::openActions,
                session::setOpenActions,
                () -> {});

        refListEditor.applyRef(context(), list, "sound", "PLING", -1);

        List<Ref> open = session.openActions();
        assertThat(open).hasSize(1);
        assertThat(open.get(0).id()).isEqualTo("sound");
        assertThat(open.get(0).value()).isEqualTo("PLING");
    }

    @Test
    void theCommandEditorSetsAnOpenCommandName() {
        properties.open(player, viewer, "alpha");
        MenuEditSession session = session();
        TextProperty name = (TextProperty)
                commandEditor.propertyAt(COMMAND_NAME_SLOT, session, "alpha").orElseThrow();

        name.applyInput(context(), "shop");

        assertThat(session.command()).isPresent();
        assertThat(session.command().orElseThrow().name()).isEqualTo("shop");
    }

    @Test
    void savingWritesTheEditedMenuAndItsCommandBlock() throws IOException {
        properties.open(player, viewer, "alpha");
        MenuEditSession session = session();
        session.setTitle("<gold>Saved");
        session.setRows(5);
        session.setChestOnly(true);
        session.setOpenActions(List.of(Ref.parse("sound:PLING")));

        fireClick(SAVE_SLOT, ClickType.LEFT);

        MenuSpec reloaded = menus.registeredSpec("alpha").orElseThrow();
        assertThat(reloaded.title()).isEqualTo("<gold>Saved");
        assertThat(reloaded.rows()).isEqualTo(5);
        assertThat(reloaded.chestOnly()).isTrue();
        assertThat(reloaded.openActions()).extracting(Ref::id).containsExactly("sound");
    }

    /** A hand-built editor click context for driving a property/ref-list apply seam directly (no live anvil). */
    private ClickContext context() {
        return new ClickContext(player, viewer, false, false, () -> {}, menus.selectorOpener(), menus.confirmOpener());
    }

    private MenuEditSession session() {
        return properties.editSession(viewer.uuid()).orElseThrow();
    }

    // --- helpers ---

    private CustomMenuLoader.SingleLoad reloadOne(String name) {
        CustomMenuLoader.SingleLoad result = loader.loadSingle(menusDir, name);
        if (result.found() && result.loaded() > 0 && !names.contains(name)) {
            names.add(name);
        }
        return result;
    }

    private void forget(String name) {
        menus.unregisterSpec(name);
        names.remove(name);
    }

    private void writeMenu(String name, int rows, String item) {
        String hocon = "rows = " + rows + "\nitems { " + item + " }\n";
        try {
            Files.writeString(menusDir.resolve(name + ".conf"), hocon);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to seed menu " + name, failure);
        }
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView openView = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                openView, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

    private static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
