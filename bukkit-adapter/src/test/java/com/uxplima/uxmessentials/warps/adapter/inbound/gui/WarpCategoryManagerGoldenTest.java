package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmessentials.shared.menu.TileText;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The warp-category manager golden test: the engine-rendered manager must draw the exact grid the original bespoke
 * category manager drew on its own {@code Bukkit.createInventory}. The fixture is two categories (a
 * {@code DIAMOND}-iconed "pvp" and a default-BOOK "misc") over the six-row layout (content slots 0..44, gray-glass
 * filler), so page 0 places one icon per category at content slots 0 and 1, the EMERALD_BLOCK "create category" button
 * at slot 49, and the back ARROW at slot 53. The window is snapshotted as {@code (slot -> material, plain name)} and
 * asserted equal, slot for slot, to the analytic baseline the old view produced. Category icons, the two fixed
 * buttons, and the engine's mandatory nav arrows at 45/46.
 *
 * <p>Then, through the engine's own {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener},
 * a left click on the first category opens that category's still-bespoke 27-slot settings panel, the back button
 * invokes the manager's back seam, and the create seam saves a new category and opens its settings, proving the
 * migrated path runs what the old click did, in both appearance and behaviour.
 */
class WarpCategoryManagerGoldenTest {

    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 46;
    private static final int CREATE_SLOT = 49;
    private static final int BACK_SLOT = 53;

    private static final WarpCategory PVP =
            new WarpCategory("pvp", "<red>PvP</red>", Optional.of("DIAMOND"), List.of(), 0, Optional.empty());
    private static final WarpCategory MISC =
            new WarpCategory("misc", "<gray>Misc</gray>", Optional.empty(), List.of(), 0, Optional.empty());

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private Scheduler scheduler;
    private TestMenuEngine engine;
    private RecordingCategories categories;
    private WarpCategoryManagerMenu manager;
    private AtomicReference<PlayerRef> backTarget;

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path tempFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler = new SyncScheduler();
        engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        categories = new RecordingCategories(List.of(PVP, MISC));
        backTarget = new AtomicReference<>();
        TextInput textInput = org.mockito.Mockito.mock(TextInput.class);
        manager = new WarpCategoryManagerMenu(engine.menus(), new KeyMessages(), scheduler, categories, textInput);
        manager.register(engine.bindings(), tempFolder, NOOP);
        // A category click and a create open the engine-rendered settings panel; the back button records its target.
        // The settings panel renders through the same engine and its spec is registered, so the click assertions then
        // see a menu-backed window. Both seams are bound after the manager exists, exactly as in production wiring.
        WarpCategorySettingsView settingsView = new WarpCategorySettingsView(
                engine.menus(), new KeyMessages(), textInput, categories, (p, v) -> backTarget.set(v));
        WarpCategoryParentSelectorMenu parentSelector = new WarpCategoryParentSelectorMenu(
                engine.menus(), new KeyMessages(), scheduler, categories, settingsView);
        parentSelector.register(engine.bindings(), tempFolder, NOOP);
        settingsView.bind(parentSelector);
        settingsView.register(engine.bindings(), tempFolder, NOOP);
        manager.bind(settingsView, (p, v) -> backTarget.set(v));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameCategoryGridButtonsAndFillerAsTheOldView() {
        manager.open(player, viewer);

        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> rendered = snapshot(player.getOpenInventory().getTopInventory());

        assertThat(rendered.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(rendered).isEqualTo(baseline);
    }

    @Test
    void theEngineWindowIsMenuBacked() {
        manager.open(player, viewer);
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void clickingACategoryOpensItsEngineSettingsPanel() {
        manager.open(player, viewer);

        fireClick(0); // content slot 0 holds "pvp"

        Inventory open = player.getOpenInventory().getTopInventory();
        // The engine category-settings window is a three-row (27-slot) menu-backed panel, not the six-row manager grid.
        assertThat(open.getSize()).isEqualTo(27);
        assertThat(open.getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void clickingBackInvokesTheManagersBackSeam() {
        manager.open(player, viewer);

        fireClick(BACK_SLOT);

        assertThat(backTarget.get()).isEqualTo(viewer);
    }

    @Test
    void theCreateSeamSavesANewCategoryAndOpensItsSettings() {
        manager.open(player, viewer);

        manager.create(player, viewer, "events");

        assertThat(categories.lastSaved())
                .hasValueSatisfying(saved -> assertThat(saved.id()).isEqualTo("events"));
        Inventory open = player.getOpenInventory().getTopInventory();
        assertThat(open.getSize()).isEqualTo(27);
        assertThat(open.getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void theCreateSeamRejectsAnIdWithASpaceWithoutSaving() {
        manager.open(player, viewer);

        manager.create(player, viewer, "two words");

        assertThat(categories.lastSaved()).isEmpty();
    }

    /**
     * The slot -> (material, plain name) map the bespoke {@code WarpCategoryManagerMenu} produced for this fixture: a
     * DIAMOND for "pvp" at content slot 0, a BOOK for the materialless "misc" at slot 1 (the old fallback), the
     * EMERALD_BLOCK "create category" button at slot 49 and the back ARROW at slot 53, each named through the catalog
     * key the test's {@code KeyMessages} returns verbatim, plus the engine's mandatory ARROW nav at 45 and 46. The
     * gray-glass filler slots are dropped from the snapshot, so a wrong material, name, or misplaced icon shows up as a
     * mismatch.
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.DIAMOND, "PvP"));
        baseline.put(1, new Snapshot(Material.BOOK, "Misc"));
        baseline.put(PREV_SLOT, new Snapshot(Material.ARROW, WarpsMessageKey.WARP_MENU_PREV.key()));
        baseline.put(NEXT_SLOT, new Snapshot(Material.ARROW, WarpsMessageKey.WARP_MENU_NEXT.key()));
        baseline.put(
                CREATE_SLOT,
                new Snapshot(Material.EMERALD_BLOCK, WarpsMessageKey.WARP_EDITOR_CATEGORY_CREATE_BUTTON_NAME.key()));
        baseline.put(BACK_SLOT, new Snapshot(Material.ARROW, WarpsMessageKey.WARP_EDITOR_SELECTOR_BACK.key()));
        return baseline;
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
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

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    /** A category repository over a fixed, mutable list that records the category the create/save path stores. */
    private static final class RecordingCategories implements WarpCategoryRepository {
        private final List<WarpCategory> categories;
        private @Nullable WarpCategory saved;

        RecordingCategories(List<WarpCategory> categories) {
            this.categories = new ArrayList<>(categories);
        }

        /** The last category {@link #save} stored, or empty if none was saved: read by the create-seam assertions. */
        Optional<WarpCategory> lastSaved() {
            return Optional.ofNullable(saved);
        }

        @Override
        public Optional<WarpCategory> find(String id) {
            return categories.stream().filter(c -> c.id().equals(id)).findFirst();
        }

        @Override
        public List<WarpCategory> all() {
            return List.copyOf(categories);
        }

        @Override
        public void save(WarpCategory category) {
            this.saved = category;
            categories.removeIf(c -> c.id().equals(category.id()));
            categories.add(category);
        }

        @Override
        public void delete(String id) {
            categories.removeIf(c -> c.id().equals(id));
        }
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
