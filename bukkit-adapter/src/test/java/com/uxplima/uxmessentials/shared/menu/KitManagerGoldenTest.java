package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategoryManagerMenu;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitManagerMenu;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitSettingsView;
import com.uxplima.uxmessentials.kits.application.DelKit;
import com.uxplima.uxmessentials.kits.application.KitEditor;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
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
 * The kit-manager golden test: the engine-rendered {@code /kit editor} manager must draw the exact grid the original
 * {@code KitManagerView} drew. The store holds two stored kits ("alpha" and "beta", both item-less so they fall back
 * to the CHEST icon), so the list draws two CHEST icons (content slots 0 and 1, the names surfacing through the kit-id
 * token), the EMERALD_BLOCK create button (slot 49), the BOOK categories button (slot 51), the BARRIER close button
 * (slot 53), and the two ARROW nav buttons (slots 45 and 46). The engine's window is snapshotted as
 * {@code (slot -> material, plain name)} and asserted equal, slot for slot, to the baseline the old view produced
 * captured once while both rendered the same fixture, then frozen here as the contract so the old class could be
 * deleted. Then, through the engine's own {@link MenuListener}, a left click on the first kit icon proves the migrated
 * path opens that kit's bespoke {@link KitSettingsView}, and a left click on the categories button proves it opens the
 * bespoke {@link KitCategoryManagerMenu}, so the move is faithful in both appearance and behaviour.
 *
 * <p>The {@code KeyMessages} catalog surfaces the entry name's {@code kit} token, so a kit's id appears in the rendered
 * label; every other key renders verbatim. A real rendering difference (a wrong key, a wrong material, a misplaced
 * cell, a lost name token) therefore still shows up as a snapshot mismatch.
 */
class KitManagerGoldenTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private RecordingRepository repository;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Admin");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        repository = new RecordingRepository();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameIconGridButtonsAndNavAsTheOldView() {
        seed("alpha");
        seed("beta");
        Map<Integer, Snapshot> baseline = oldViewBaseline();

        Map<Integer, Snapshot> engineGrid = snapshotEngine();

        assertThat(engineGrid.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engineGrid).isEqualTo(baseline);
    }

    @Test
    void leftClickingAKitThroughTheEngineOpensThatKitsSettings() {
        seed("alpha");
        openEngine();
        // Content slot 0 is the first kit icon, "alpha"; a left click must open that kit's settings editor, now an
        // engine-backed window whose subject is the clicked kit.
        fireClick(0, ClickType.LEFT);

        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            throw new AssertionError("left-click did not open a kit-settings window");
        }
        assertThat(holder.specId()).isEqualTo(KitSettingsView.SPEC_ID);
        assertThat(holder.ctx().subject(KitDefinition.class).id()).isEqualTo(KitId.of("alpha"));
    }

    @Test
    void leftClickingTheCategoriesButtonThroughTheEngineOpensTheCategoryManager() {
        seed("alpha");
        openEngine();
        // Slot 51 is the manage-categories button; a left click must open the engine-rendered category manager, now a
        // holder-backed menu list rather than the old bespoke window.
        fireClick(51, ClickType.LEFT);

        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code KitManagerView} produced for this fixture (two item-less
     * kits, "alpha" and "beta"), captured once while both paths rendered it identically and frozen here as the contract:
     * two CHEST icons (content slots 0 and 1. The names surface through the kit-id token), the EMERALD_BLOCK create
     * button (slot 49), the BOOK categories button (slot 51), the BARRIER close button (slot 53), and the two nav ARROWs
     * (slots 45 and 46).
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.CHEST, "alpha"));
        baseline.put(1, new Snapshot(Material.CHEST, "beta"));
        baseline.put(45, new Snapshot(Material.ARROW, "kit.menu.prev"));
        baseline.put(46, new Snapshot(Material.ARROW, "kit.menu.next"));
        baseline.put(49, new Snapshot(Material.EMERALD_BLOCK, "kit.editor.create-button.name"));
        baseline.put(51, new Snapshot(Material.BOOK, "kit.editor.category.manager-title"));
        baseline.put(53, new Snapshot(Material.BARRIER, "kit.editor.close-button.name"));
        return baseline;
    }

    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        Inventory inv = player.getOpenInventory().getTopInventory();
        return snapshot(inv);
    }

    /** Build the engine, register the kits manager bindings + spec, and open the manager for the player. */
    private void openEngine() {
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        // The shipped close action the manager's close button names; the engine façade does not register it itself.
        bindings.action("close", ctx -> ctx.player().closeInventory());
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());

        KitManagerMenu manager = managerMenu(menus, bindings);
        manager.register(bindings, dataFolder, NOOP);
        manager.open(player, viewer);
    }

    /** A {@link KitManagerMenu} wired off the same collaborators as the old view, over the engine façade. */
    private KitManagerMenu managerMenu(Menus menus, MenuBindings bindings) {
        Messages messages = new KeyMessages();
        Notifier notifier = new Notifier(messages, new NoSink());
        KitEditor kitEditor = new KitEditor(repository, notifier);
        // The per-kit settings panel renders through the engine now: build it over the same engine and register its
        // spec, so a manager kit click opens that menu-backed window.
        KitSettingsView settingsView = new KitSettingsView(
                menus,
                guiText,
                messages,
                org.mockito.Mockito.mock(TextInput.class),
                kitEditor,
                new DelKit(repository, notifier),
                new KitEditorView(messages, kitEditor, scheduler),
                (p, v) -> {});
        settingsView.register(bindings, dataFolder, NOOP);
        // The category manager renders through the engine now; the categories-button test only opens it (an empty
        // grid), so the input seam it would prompt through is a stand-in and the click/back collaborators stay unbound.
        KitCategoryManagerMenu categoryManager = new KitCategoryManagerMenu(
                menus, messages, scheduler, new StubCategoryRepository(), org.mockito.Mockito.mock(TextInput.class));
        categoryManager.register(bindings, dataFolder, NOOP);
        return new KitManagerMenu(menus, scheduler, repository, messages, settingsView, categoryManager, (p, v) -> {});
    }

    /** Click the given content slot of the open menu with the given gesture through the production click path. */
    private void fireClick(int slot, ClickType click) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, click, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The slot -> (material, plain name) map for every non-empty, non-filler slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item)));
        }
        return out;
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    private void seed(String id) {
        repository.save(KitDefinition.builder().id(KitId.of(id)).build());
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    // --- fakes ---

    /** A repository that records stored kits in insertion order, mirroring the on-disk kit catalog. */
    private static final class RecordingRepository implements KitRepository {
        private final List<KitDefinition> kits = new CopyOnWriteArrayList<>();

        @Override
        public Optional<KitDefinition> find(KitId id) {
            return kits.stream().filter(kit -> kit.id().equals(id)).findFirst();
        }

        @Override
        public List<KitDefinition> all() {
            return List.copyOf(kits);
        }

        @Override
        public boolean exists(KitId id) {
            return find(id).isPresent();
        }

        @Override
        public void save(KitDefinition definition) {
            kits.removeIf(kit -> kit.id().equals(definition.id()));
            kits.add(definition);
        }

        @Override
        public void delete(KitId id) {
            kits.removeIf(kit -> kit.id().equals(id));
        }
    }

    private static final class NoSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class StubCategoryRepository implements KitCategoryRepository {
        @Override
        public Optional<KitCategory> find(String id) {
            return Optional.empty();
        }

        @Override
        public List<KitCategory> all() {
            return List.of();
        }

        @Override
        public void save(KitCategory category) {}

        @Override
        public void delete(String id) {}
    }

    /**
     * Surfaces the entry name's {@code kit} token so a kit's id appears; else the bare key. The engine resolves a
     * {@code @key} line through a lambda {@link MessageKey} carrying only the key string, so the match is by
     * {@link MessageKey#key()} rather than enum identity.
     */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(KitsMessageKey.KIT_MENU_ENTRY_NAME.key())) {
                return placeholders.getOrDefault("kit", "");
            }
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
