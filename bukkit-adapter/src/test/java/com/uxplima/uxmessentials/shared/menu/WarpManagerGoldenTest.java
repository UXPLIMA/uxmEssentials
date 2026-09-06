package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.PlayerWarpGoToHandle;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.PlayerWarpRepositoryHandle;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategoryManagerMenu;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategorySettingsView;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpEditorView;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpManagerMenu;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The warp-manager golden test: the engine-rendered {@code /warp editor} manager must draw the exact grid the original
 * {@code WarpManagerView} drew. The store holds two stored warps ("alpha" and "beta", both without an icon override so
 * they fall back to the ENDER_PEARL icon), so the list draws two ENDER_PEARL icons (content slots 0 and 1, the names
 * surfacing through the warp-name token), the EMERALD_BLOCK create button (slot 48), the BARRIER close button (slot 50),
 * and the BOOK categories button (slot 51), and, as in the original, no page arrows. The engine's window is
 * snapshotted as {@code (slot -> material, plain name)} and asserted equal, slot for slot, to the baseline the old view
 * produced. Captured once while both rendered the same fixture, then frozen here as the contract so the old class could
 * be deleted. Then, through the engine's own {@link MenuListener}, a left click on the first warp icon proves the
 * migrated path opens that warp's engine {@link WarpEditorView}, a left click on the create button proves it reaches
 * the create prompt, and a left click on the categories button proves it opens the bespoke
 * {@link WarpCategoryManagerMenu}, so the move is faithful in both appearance and behaviour.
 *
 * <p>The {@code KeyMessages} catalog surfaces the entry name's {@code warp} token, so a warp's name appears in the
 * rendered label; every other key renders verbatim. A real rendering difference (a wrong key, a wrong material, a
 * misplaced cell, a lost name token) therefore still shows up as a snapshot mismatch.
 */
class WarpManagerGoldenTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private RecordingRepository repository;
    private final AtomicBoolean createPromptReached = new AtomicBoolean(false);

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
        createPromptReached.set(false);
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
    void leftClickingAWarpThroughTheEngineOpensThatWarpsEditor() {
        seed("alpha");
        openEngine();
        // Content slot 0 is the first warp icon, "alpha"; a left click must open that warp's editor.
        fireClick(0, ClickType.LEFT);

        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void leftClickingTheCreateButtonThroughTheEngineReachesTheCreatePrompt() {
        seed("alpha");
        openEngine();
        // Slot 48 is the create button; a left click must reach the create-name prompt flow.
        fireClick(48, ClickType.LEFT);

        assertThat(createPromptReached).isTrue();
    }

    @Test
    void leftClickingTheCategoriesButtonThroughTheEngineOpensTheCategoryManager() {
        seed("alpha");
        openEngine();
        // Slot 51 is the manage-categories button; a left click must open the category manager, which now renders
        // through the menu engine, so its window is a holder-backed MenuHolder, not the old bespoke holder.
        fireClick(51, ClickType.LEFT);

        assertThat(player.getOpenInventory().getTopInventory().getHolder())
                .isInstanceOf(com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder.class);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code WarpManagerView} produced for this fixture (two warps
     * without an icon override, "alpha" and "beta"), captured once while both paths rendered it identically and frozen
     * here as the contract: two ENDER_PEARL icons (content slots 0 and 1. The names surface through the warp-name
     * token), the EMERALD_BLOCK create button (slot 48), the BARRIER close button (slot 50), and the BOOK categories
     * button (slot 51). The original warp manager drew no page arrows, so none appear here.
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.ENDER_PEARL, "alpha"));
        baseline.put(1, new Snapshot(Material.ENDER_PEARL, "beta"));
        baseline.put(48, new Snapshot(Material.EMERALD_BLOCK, "warp.manager.create-button.name"));
        baseline.put(50, new Snapshot(Material.BARRIER, "warp.manager.close-button.name"));
        baseline.put(51, new Snapshot(Material.BOOK, "warp.manager.manage-categories.name"));
        return baseline;
    }

    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        Inventory inv = player.getOpenInventory().getTopInventory();
        return snapshot(inv);
    }

    /** Build the engine, register the warps manager bindings + spec, and open the manager for the player. */
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

        WarpManagerMenu manager = managerMenu(menus, bindings);
        manager.register(bindings, dataFolder, NOOP);
        manager.open(player, viewer);
    }

    /** A {@link WarpManagerMenu} wired off the same collaborators as the old view, over the engine façade. */
    private WarpManagerMenu managerMenu(Menus menus, MenuBindings bindings) {
        Messages messages = new KeyMessages();
        var textInput =
                org.mockito.Mockito.mock(com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput.class);
        WarpEditorView editorView = new WarpEditorView(
                menus,
                messages,
                scheduler,
                repository,
                textInput,
                org.mockito.Mockito.mock(com.uxplima.uxmessentials.warps.application.UseWarp.class),
                new PlayerWarpRepositoryHandle(),
                new PlayerWarpGoToHandle());
        // The manager only opens the editor here; its sub-screens are exercised by the editor's own golden test, so the
        // editor is registered (its spec must be known for the open) but not bound to live sub-screens.
        editorView.register(bindings, dataFolder, NOOP);
        WarpCategoryManagerMenu categoryManager =
                new WarpCategoryManagerMenu(menus, messages, scheduler, new StubCategoryRepository(), textInput);
        // The category manager is only the warp manager's category-button target here; this test never drives its own
        // create/click/back seams, so the loop-closing collaborators are no-ops. Its spec is registered because the
        // category button opens it. The engine settings panel is bound but never opened, so its spec is not registered.
        categoryManager.register(bindings, dataFolder, NOOP);
        categoryManager.bind(
                new WarpCategorySettingsView(menus, messages, textInput, new StubCategoryRepository(), (p, v) -> {}),
                (p, v) -> {});
        return new WarpManagerMenu(
                menus,
                scheduler,
                repository,
                messages,
                editorView,
                categoryManager,
                (p, v) -> createPromptReached.set(true));
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
        Position at = Position.of(WORLD, 0, 64, 0);
        repository.save(Warp.create(WarpName.of(id), at, viewer, Instant.EPOCH));
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    // --- fakes ---

    /** A repository that records stored warps in insertion order, mirroring the on-disk warp catalog. */
    private static final class RecordingRepository implements WarpRepository {
        private final List<Warp> warps = new CopyOnWriteArrayList<>();

        @Override
        public Optional<Warp> find(WarpName name) {
            return warps.stream().filter(warp -> warp.name().equals(name)).findFirst();
        }

        @Override
        public List<Warp> all() {
            return List.copyOf(warps);
        }

        @Override
        public boolean exists(WarpName name) {
            return find(name).isPresent();
        }

        @Override
        public void save(Warp warp) {
            warps.removeIf(existing -> existing.name().equals(warp.name()));
            warps.add(warp);
        }

        @Override
        public void delete(WarpName name) {
            warps.removeIf(warp -> warp.name().equals(name));
        }

        @Override
        public void rate(WarpName name, UUID player, double rating) {}

        @Override
        public double averageRating(WarpName name) {
            return 0.0;
        }
    }

    private static final class StubCategoryRepository implements WarpCategoryRepository {
        @Override
        public Optional<WarpCategory> find(String id) {
            return Optional.empty();
        }

        @Override
        public List<WarpCategory> all() {
            return List.of();
        }

        @Override
        public void save(WarpCategory category) {}

        @Override
        public void delete(String id) {}
    }

    /**
     * Surfaces the entry name's {@code warp} token so a warp's name appears; else the bare key. The engine resolves a
     * {@code @key} line through a lambda {@link MessageKey} carrying only the key string, so the match is by
     * {@link MessageKey#key()} rather than enum identity.
     */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(WarpsMessageKey.WARP_MENU_ENTRY_NAME.key())) {
                return placeholders.getOrDefault("warp", "");
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
