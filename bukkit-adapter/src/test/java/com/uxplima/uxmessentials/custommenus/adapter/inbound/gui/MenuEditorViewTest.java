package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecPersistence;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecWriter;
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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /menu editor} picker: it renders one row per loaded menu, a row click opens the
 * per-menu overview, the overview's save button rewrites the file, and delete is gated behind the engine confirm and
 * then removes the file and unregisters the menu. Create is driven through the view's apply seam (the anvil prompt is
 * not fired live, matching the other editor view tests), and it lands a new loadable file in the engine. Everything is
 * an engine window, a {@link MenuHolder} backs each, so the editor never touches a raw Bukkit inventory.
 */
class MenuEditorViewTest {

    private static final int SAVE_SLOT = 10;
    private static final int DELETE_SLOT = 26;
    private static final int CONFIRM_YES_SLOT = 11; // the engine confirm window's yes button (ConfirmRenderer.YES_SLOT)

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private Menus menus;
    private CustomMenuLoader loader;
    private final List<String> names = new ArrayList<>();
    private MenuEditorView view;

    @TempDir
    Path menusDir;

    @BeforeEach
    void setUp(@TempDir Path guiDir) {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        Guis.install(plugin);
        GuiText guiText = new GuiText(new KeyMessages());
        Scheduler scheduler = new SyncScheduler();
        TextInput textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);

        MenuBindings bindings = new MenuBindings();
        bindings.action("close", ctx -> {});
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
        writeMenu("alpha", "<gold>Alpha");
        writeMenu("beta", "<aqua>Beta");
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
        view = new MenuEditorView(
                menus,
                guiText,
                scheduler,
                new KeyMessages(),
                service,
                new MenuEditLocks(),
                () -> List.copyOf(names),
                menus::registeredSpec,
                new GuiLayouts(guiDir, NOOP),
                textInput,
                (player, id) -> {},
                (player, id) -> {});
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void pickerRendersARowPerLoadedMenu() {
        view.open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        // alpha then beta (the loader sorts the files); one chest icon per menu at the content slots.
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.CHEST);
        assertThat(inv.getItem(1).getType()).isEqualTo(Material.CHEST);
        // The create button sits at its layout slot on the bottom row.
        assertThat(inv.getItem(49).getType()).isEqualTo(Material.LIME_DYE);
    }

    @Test
    void clickingAMenuOpensItsOverview() {
        view.open(player, viewer);

        fireClick(0);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        // The overview's save button renders at its property slot.
        assertThat(inv.getItem(SAVE_SLOT).getType()).isEqualTo(Material.EMERALD);
    }

    @Test
    void creatingAMenuWritesALoadableFileAndRegistersIt() {
        view.applyCreate(player, viewer, "welcome");

        assertThat(Files.exists(menusDir.resolve("welcome.conf"))).isTrue();
        assertThat(menus.registeredSpec("welcome")).isPresent();
        assertThat(names).contains("welcome");
    }

    @Test
    void savingRewritesTheMenuFile() throws IOException {
        Files.delete(menusDir.resolve("alpha.conf")); // prove the save recreates it
        view.open(player, viewer);
        fireClick(0); // open alpha's overview

        fireClick(SAVE_SLOT);

        assertThat(Files.exists(menusDir.resolve("alpha.conf"))).isTrue();
    }

    @Test
    void deleteIsGatedByAConfirmAndThenRemovesTheMenu() {
        view.open(player, viewer);
        fireClick(0); // open alpha's overview

        fireClick(DELETE_SLOT); // opens the confirm, does not delete yet
        assertThat(Files.exists(menusDir.resolve("alpha.conf"))).isTrue();
        assertThat(menus.registeredSpec("alpha")).isPresent();

        fireClick(CONFIRM_YES_SLOT); // confirm the delete
        assertThat(Files.exists(menusDir.resolve("alpha.conf"))).isFalse();
        assertThat(menus.registeredSpec("alpha")).isEmpty();
        assertThat(names).doesNotContain("alpha");
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

    private void writeMenu(String name, String title) {
        String hocon = "title = \"" + title + "\"\nrows = 1\n"
                + "items { x { slot = 0, material = STONE, click { left = [\"close\"] } } }\n";
        try {
            Files.writeString(menusDir.resolve(name + ".conf"), hocon);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to seed menu " + name, failure);
        }
    }

    private void fireClick(int slot) {
        InventoryView openView = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                openView, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
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
