package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpBrowseMenu;
import com.uxplima.uxmessentials.warps.application.UseWarp;
import com.uxplima.uxmessentials.warps.application.WarpAccess;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpTeleporter;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The warp-browse golden test: the engine-rendered {@code /warp} browse menu must draw the exact grid the original
 * {@code WarpMenuView} drew, in both its modes, and click the same way.
 *
 * <p>Legacy (no categories): two warps "alpha" and "beta" without an icon override fall back to the ENDER_PEARL
 * icon, so page 0 holds two ENDER_PEARL tiles (content slots 0 and 1, the names surfacing through the warp-name
 * token) and the two ARROW nav buttons (slots 48 and 50), with no back button at the root. The engine's window is
 * snapshotted as {@code (slot -> material, plain name)} and asserted equal, slot for slot, to the baseline the old
 * view produced. Captured once while both rendered the same fixture, then frozen here as the contract so the old
 * class could be deleted. A priced, permission-gated warp is also rendered to prove its lore expands to the full
 * four lines through the single multi-line placeholder, where a free warp expands to two.
 *
 * <p>Category mode: a "places" category (a BOOK tile) sits alongside an uncategorised warp "beta" at the root. A
 * left click on the category drills in. The engine re-opens at that level, where the warp filed under it appears;
 * a left click on the back button steps back to the root. And, through the engine's own {@link MenuListener}, a
 * left click on a warp tile proves the migrated path warps the viewer through the same {@link UseWarp} use case the
 * {@code /warp} command drives, recorded by the teleporter the use case delegates the hop to.
 */
class WarpBrowseGoldenTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private SyncScheduler scheduler;
    private RecordingRepository repository;
    private StubCategoryRepository categories;
    private RecordingTeleporter teleporter;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler = new SyncScheduler();
        repository = new RecordingRepository();
        categories = new StubCategoryRepository();
        teleporter = new RecordingTeleporter();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void legacyModeRendersTheSameIconGridAndNavAsTheOldView() {
        seedWarp(Warp.create(WarpName.of("alpha"), at(), viewer, Instant.EPOCH));
        seedWarp(Warp.create(WarpName.of("beta"), at(), viewer, Instant.EPOCH));

        Map<Integer, Snapshot> engineGrid = snapshotEngine();

        assertThat(engineGrid).isEqualTo(legacyBaseline());
    }

    @Test
    void aPricedGatedWarpExpandsToItsFourLoreLinesWhereAFreeOneExpandsToTwo() {
        seedWarp(Warp.create(WarpName.of("free"), at(), viewer, Instant.EPOCH));
        seedWarp(Warp.create(
                WarpName.of("vip"),
                at(),
                viewer,
                Instant.EPOCH,
                WarpCost.of(new BigDecimal("100")),
                Optional.of("vip.node")));
        Inventory inv = openLegacy();

        // "free": location + click-to-warp = 2 lines; "vip": cost + permission + location + click-to-warp = 4.
        assertThat(loreLines(inv.getItem(0))).isEqualTo(2);
        assertThat(loreLines(inv.getItem(1))).isEqualTo(4);
    }

    @Test
    void categoryModeMixesACategoryTileWithTheRootWarps() {
        seedCategory("places", Optional.empty(), 0);
        seedWarp(categorised(Warp.create(WarpName.of("alpha"), at(), viewer, Instant.EPOCH), "places"));
        seedWarp(Warp.create(WarpName.of("beta"), at(), viewer, Instant.EPOCH));

        Map<Integer, Snapshot> engineGrid = snapshotEngine();

        assertThat(engineGrid).isEqualTo(categoryRootBaseline());
    }

    @Test
    void clickingACategoryDrillsIntoItAndBackReturnsToTheRoot() {
        seedCategory("places", Optional.empty(), 0);
        seedWarp(categorised(Warp.create(WarpName.of("alpha"), at(), viewer, Instant.EPOCH), "places"));
        seedWarp(Warp.create(WarpName.of("beta"), at(), viewer, Instant.EPOCH));
        openCategoryRoot();

        // Slot 0 is the "places" category tile; a left click drills into it, where its warp "alpha" appears.
        fireClick(0, ClickType.LEFT);
        assertThat(snapshot(top())).isEqualTo(drilledBaseline());

        // Slot 49 is the back button shown below the root; a left click steps back to the root grid.
        fireClick(49, ClickType.LEFT);
        assertThat(snapshot(top())).isEqualTo(categoryRootBaseline());
    }

    @Test
    void leftClickingAWarpThroughTheEngineWarpsTheViewerThroughUseWarp() {
        seedWarp(Warp.create(WarpName.of("alpha"), at(), viewer, Instant.EPOCH));
        openLegacy();

        // Content slot 0 is the "alpha" warp tile; a left click must warp the viewer there through UseWarp.
        fireClick(0, ClickType.LEFT);

        assertThat(teleporter.hops).hasSize(1);
        assertThat(teleporter.hops.get(0).name().value()).isEqualTo("alpha");
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code WarpMenuView} produced for the two-warp legacy fixture,
     * frozen as the contract: two ENDER_PEARL tiles (content slots 0 and 1, the names surfacing through the warp-name
     * token) and the two nav ARROWs (slots 48 and 50). The root level shows no back button.
     */
    private static Map<Integer, Snapshot> legacyBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.ENDER_PEARL, "alpha"));
        baseline.put(1, new Snapshot(Material.ENDER_PEARL, "beta"));
        baseline.put(48, new Snapshot(Material.ARROW, "warp.menu.prev"));
        baseline.put(50, new Snapshot(Material.ARROW, "warp.menu.next"));
        return baseline;
    }

    /** The category-mode root: the "places" BOOK category tile, the uncategorised "beta" warp, and the nav ARROWs. */
    private static Map<Integer, Snapshot> categoryRootBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.BOOK, "places"));
        baseline.put(1, new Snapshot(Material.ENDER_PEARL, "beta"));
        baseline.put(48, new Snapshot(Material.ARROW, "warp.menu.prev"));
        baseline.put(50, new Snapshot(Material.ARROW, "warp.menu.next"));
        return baseline;
    }

    /** Inside the "places" category: its warp "alpha", the nav ARROWs, and the back ARROW shown below the root. */
    private static Map<Integer, Snapshot> drilledBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.ENDER_PEARL, "alpha"));
        baseline.put(48, new Snapshot(Material.ARROW, "warp.menu.prev"));
        baseline.put(49, new Snapshot(Material.ARROW, "warp.menu.back"));
        baseline.put(50, new Snapshot(Material.ARROW, "warp.menu.next"));
        return baseline;
    }

    private Map<Integer, Snapshot> snapshotEngine() {
        return snapshot(openLegacy());
    }

    /** Open the browse menu over a real engine, returning the top inventory the window populated. */
    private Inventory openLegacy() {
        browseMenu().open(player, viewer, repository.all());
        return top();
    }

    /** Open the category-mode root (categories present), returning nothing: the window is read off the player. */
    private void openCategoryRoot() {
        browseMenu().open(player, viewer, repository.all());
    }

    /** A {@link WarpBrowseMenu} wired off the same collaborators the old view used, over a real engine façade. */
    private WarpBrowseMenu browseMenu() {
        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.bindings().action("close", ctx -> ctx.player().closeInventory());
        engine.installListener(plugin);
        Permissions permissions = new AllowAllPermissions();
        Notifier notifier = new Notifier(new KeyMessages(), (v, text) -> {});
        WarpAccess access = new WarpAccess(permissions, Optional.<WarpEconomy>empty());
        UseWarp useWarp =
                new UseWarp(repository, access, teleporter, notifier, position -> true, permissions, scheduler);
        WarpBrowseMenu menu = new WarpBrowseMenu(engine.menus(), scheduler, useWarp, new KeyMessages(), categories);
        menu.register(engine.bindings(), dataFolder, NOOP);
        return menu;
    }

    private Inventory top() {
        return player.getOpenInventory().getTopInventory();
    }

    /** Click the given slot of the open menu with the given gesture through the production click path. */
    private void fireClick(int slot, ClickType click) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, click, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The slot -> (material, plain name) map for every non-empty slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item)));
        }
        return out;
    }

    /** The number of lore lines on a rendered tile, proving the multi-line placeholder expanded variable lore. */
    private static int loreLines(ItemStack item) {
        // The body under the title line: the count the variable-lore placeholder is responsible for.
        return TileText.body(item).size();
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    private static Position at() {
        return Position.of(WORLD, 0, 64, 0);
    }

    private static Warp categorised(Warp warp, String categoryId) {
        return warp.withCategoryId(Optional.of(categoryId));
    }

    private void seedWarp(Warp warp) {
        repository.save(warp);
    }

    private void seedCategory(String id, Optional<String> parent, int slot) {
        categories.save(new WarpCategory(id, id, Optional.empty(), List.of(), slot, parent));
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

    /** Records stored categories in insertion order so the browse menu groups by them. */
    private static final class StubCategoryRepository implements WarpCategoryRepository {
        private final List<WarpCategory> categories = new ArrayList<>();

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
            categories.removeIf(existing -> existing.id().equals(category.id()));
            categories.add(category);
        }

        @Override
        public void delete(String id) {
            categories.removeIf(c -> c.id().equals(id));
        }
    }

    /** Records every hop so a warp click is asserted by the use case delegating the teleport here. */
    private static final class RecordingTeleporter implements WarpTeleporter {
        private final List<Warp> hops = new ArrayList<>();

        @Override
        public void teleportTo(PlayerRef who, Warp warp) {
            hops.add(warp);
        }
    }

    private static final class AllowAllPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return true;
        }

        @Override
        public Permissions.QuotaResult resolveQuota(
                PlayerRef who,
                Permissions.QuotaFamily family,
                @org.jspecify.annotations.Nullable WorldRef world,
                long configDefault) {
            return Permissions.QuotaResult.unlimited();
        }
    }

    /**
     * Surfaces the warp-name and category-name tokens so a tile's name appears; every other key (the lore lines, the
     * nav and back labels) renders as its bare key, which is enough for the slot-and-lore-count assertions. The engine
     * resolves a {@code @key} line through a lambda {@link MessageKey} carrying only the key string, so the match is by
     * {@link MessageKey#key()} rather than enum identity.
     */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(WarpsMessageKey.WARP_MENU_ENTRY_NAME.key())) {
                return placeholders.getOrDefault("warp", "");
            }
            if (key.key().equals(WarpsMessageKey.WARP_MENU_CATEGORY_NAME.key())) {
                return placeholders.getOrDefault("category", "");
            }
            return key.key();
        }
    }

    private static final com.uxplima.uxmessentials.shared.application.port.Logger NOOP =
            new com.uxplima.uxmessentials.shared.application.port.Logger() {
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
