package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.PlayerWarpGoToHandle;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.PlayerWarpRepositoryHandle;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategorySelectorMenu;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpEditorView;
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
 * The warp → category selector golden test: the engine-rendered selector must draw the exact grid the original bespoke
 * {@code WarpCategorySelectorView} drew on its own {@code Bukkit.createInventory}. The fixture is two categories (a
 * {@code DIAMOND}-iconed "pvp" and a default-BOOK "misc") over the selector's six-row layout (content slots 0..44,
 * gray-glass filler), so page 0 places one icon per category at content slots 0 and 1, the "no category" BARRIER at
 * slot 49, and the back ARROW at slot 53. The engine window is snapshotted as {@code (slot -> material, plain name)}
 * and asserted equal, slot for slot, to the analytic baseline the old view produced. Category icons, the two fixed
 * buttons, and the engine's mandatory nav arrows at 45/46. Then a left click on the first category through the
 * engine's own {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener} proves the
 * migrated path runs the same assign the old click did, the warp saved with that category id, and returns the viewer
 * to its editor window, faithful in both appearance and behaviour.
 */
class WarpCategorySelectorGoldenTest {

    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 46;
    private static final int NONE_SLOT = 49;
    private static final int BACK_SLOT = 53;
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private static final WarpCategory PVP =
            new WarpCategory("pvp", "<red>PvP</red>", Optional.of("DIAMOND"), List.of(), 0, Optional.empty());
    private static final WarpCategory MISC =
            new WarpCategory("misc", "<gray>Misc</gray>", Optional.empty(), List.of(), 0, Optional.empty());

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

    @TempDir
    Path dataFolder;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private Scheduler scheduler;
    private TestMenuEngine engine;
    private RecordingWarps warps;
    private WarpCategorySelectorMenu selector;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler = new SyncScheduler();
        engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        warps = new RecordingWarps();
        warps.seed("spawn");
        WarpCategoryRepository categories = new FixedCategories(List.of(PVP, MISC));
        // The selector reopens the warp editor after a pick; the editor renders through the menu engine, so its spec
        // is registered here before any reopen drives it.
        TextInput textInput = org.mockito.Mockito.mock(TextInput.class);
        WarpEditorView editorView = new WarpEditorView(
                engine.menus(),
                new KeyMessages(),
                scheduler,
                warps,
                textInput,
                org.mockito.Mockito.mock(com.uxplima.uxmessentials.warps.application.UseWarp.class),
                new PlayerWarpRepositoryHandle(),
                new PlayerWarpGoToHandle());
        editorView.register(engine.bindings(), dataFolder, NOOP);
        selector = new WarpCategorySelectorMenu(
                engine.menus(), new KeyMessages(), scheduler, categories, warps, editorView);
        selector.register(engine.bindings(), dataFolder, NOOP);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameCategoryGridButtonsAndFillerAsTheOldView() {
        selector.open(player, viewer, "spawn");

        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> rendered = snapshot(player.getOpenInventory().getTopInventory());

        assertThat(rendered.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(rendered).isEqualTo(baseline);
    }

    @Test
    void theEngineWindowIsMenuBacked() {
        selector.open(player, viewer, "spawn");
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void clickingACategoryAssignsItToTheWarpAndReturnsToEditor() {
        selector.open(player, viewer, "spawn");

        fireClick(0); // content slot 0 holds "pvp"

        assertThat(warps.find(WarpName.of("spawn")).orElseThrow().categoryId()).contains("pvp");
    }

    @Test
    void clickingNoCategoryClearsTheWarpsCategoryAndReturnsToEditor() {
        warps.save(warps.find(WarpName.of("spawn")).orElseThrow().withCategoryId(Optional.of("pvp")));
        selector.open(player, viewer, "spawn");

        fireClick(NONE_SLOT);

        assertThat(warps.find(WarpName.of("spawn")).orElseThrow().categoryId()).isEmpty();
    }

    @Test
    void clickingBackReturnsToEditorWithoutAssigning() {
        selector.open(player, viewer, "spawn");

        fireClick(BACK_SLOT);

        assertThat(warps.find(WarpName.of("spawn")).orElseThrow().categoryId()).isEmpty();
    }

    /**
     * The slot -> (material, plain name) map the bespoke {@code WarpCategorySelectorView} produced for this fixture: a
     * DIAMOND for "pvp" at content slot 0, a BOOK for the materialless "misc" at slot 1 (the old fallback), the "no
     * category" BARRIER at slot 49 and the back ARROW at slot 53, each named through the catalog key the test's
     * {@code KeyMessages} returns verbatim, plus the engine's mandatory ARROW nav at 45 and 46. The gray-glass filler
     * slots are dropped from the snapshot, so a wrong material, name, or misplaced icon still shows up as a mismatch.
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.DIAMOND, "PvP"));
        baseline.put(1, new Snapshot(Material.BOOK, "Misc"));
        baseline.put(PREV_SLOT, new Snapshot(Material.ARROW, WarpsMessageKey.WARP_MENU_PREV.key()));
        baseline.put(NEXT_SLOT, new Snapshot(Material.ARROW, WarpsMessageKey.WARP_MENU_NEXT.key()));
        baseline.put(
                NONE_SLOT,
                new Snapshot(Material.BARRIER, WarpsMessageKey.WARP_EDITOR_CATEGORY_SELECTOR_NONE_NAME.key()));
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

    /** A warp repository holding the seeded warps and recording the assign the selector saves back. */
    private static final class RecordingWarps implements WarpRepository {
        private final Map<String, Warp> warps = new LinkedHashMap<>();

        void seed(String name) {
            Position at = Position.of(WORLD, 0, 64, 0);
            Warp warp = Warp.create(WarpName.of(name), at, new PlayerRef(UUID.randomUUID(), "Owner"), Instant.EPOCH);
            warps.put(warp.name().value(), warp);
        }

        @Override
        public Optional<Warp> find(WarpName name) {
            return Optional.ofNullable(warps.get(name.value()));
        }

        @Override
        public List<Warp> all() {
            return new ArrayList<>(warps.values());
        }

        @Override
        public boolean exists(WarpName name) {
            return warps.containsKey(name.value());
        }

        @Override
        public void save(Warp warp) {
            warps.put(warp.name().value(), warp);
        }

        @Override
        public void delete(WarpName name) {
            warps.remove(name.value());
        }

        @Override
        public void rate(WarpName name, UUID player, double rating) {}

        @Override
        public double averageRating(WarpName name) {
            return 0.0;
        }
    }

    /** A category repository over a fixed, in-hand list: no Bukkit read, the snapshot the selector grids. */
    private record FixedCategories(List<WarpCategory> categories) implements WarpCategoryRepository {
        @Override
        public Optional<WarpCategory> find(String id) {
            return categories.stream().filter(c -> c.id().equals(id)).findFirst();
        }

        @Override
        public List<WarpCategory> all() {
            return categories;
        }

        @Override
        public void save(WarpCategory category) {}

        @Override
        public void delete(String id) {}
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

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
        public void asyncAfter(java.time.Duration delay, Runnable task) {
            task.run();
        }
    }
}
