package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategoryParentSelectorMenu;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategorySettingsView;
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
 * The parent-category selector golden test: the engine-rendered selector must draw the exact grid the original
 * bespoke {@code WarpCategoryParentSelectorMenu} drew on its own {@code Bukkit.createInventory}. The fixture edits the
 * "child" category over a three-category set, so the candidate list is the two others (a {@code DIAMOND}-iconed "pvp"
 * and a default-BOOK "misc"), drawn at content slots 0 and 1, with the "no parent" BARRIER at slot 49 and the back
 * ARROW at slot 53. The window is snapshotted as {@code (slot -> material, plain name)} and asserted equal, slot for
 * slot, to the analytic baseline the old view produced. Candidate icons, the two fixed buttons, and the engine's
 * mandatory nav arrows at 45/46, and the category being edited never appears (the cyclic-parent guard). Then a left
 * click on the first candidate through the engine's own
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener} proves the migrated path runs
 * the same assign the old click did, the edited category saved with that parent id, and returns the viewer to its
 * settings window, faithful in both appearance and behaviour.
 */
class WarpCategoryParentSelectorGoldenTest {

    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 46;
    private static final int NONE_SLOT = 49;
    private static final int BACK_SLOT = 53;

    private static final WarpCategory CHILD =
            new WarpCategory("child", "<aqua>Child</aqua>", Optional.empty(), List.of(), 0, Optional.empty());
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
    private WarpCategoryParentSelectorMenu selector;

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler = new SyncScheduler();
        engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        categories = new RecordingCategories(List.of(CHILD, PVP, MISC));
        // The settings panel the selector reopens after a pick renders through the same engine; it is registered so a
        // pick lands the viewer back on that menu-backed panel, the assign assertions' return surface.
        TextInput textInput = org.mockito.Mockito.mock(TextInput.class);
        WarpCategorySettingsView settingsView =
                new WarpCategorySettingsView(engine.menus(), new KeyMessages(), textInput, categories, (p, v) -> {});
        selector = new WarpCategoryParentSelectorMenu(
                engine.menus(), new KeyMessages(), scheduler, categories, settingsView);
        settingsView.bind(selector);
        settingsView.register(engine.bindings(), dataFolder, NOOP);
        selector.register(engine.bindings(), dataFolder, NOOP);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameCandidateGridButtonsAndFillerAsTheOldView() {
        selector.open(player, viewer, CHILD);

        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> rendered = snapshot(player.getOpenInventory().getTopInventory());

        assertThat(rendered.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(rendered).isEqualTo(baseline);
    }

    @Test
    void theEditedCategoryIsNeverAParentCandidate() {
        selector.open(player, viewer, CHILD);
        Map<Integer, Snapshot> rendered = snapshot(player.getOpenInventory().getTopInventory());

        // "Child" is the category being edited, so it must not appear in the grid (the cyclic-parent guard).
        assertThat(rendered.values()).noneMatch(s -> s.name().equals("Child"));
    }

    @Test
    void theEngineWindowIsMenuBacked() {
        selector.open(player, viewer, CHILD);
        assertThat(player.getOpenInventory().getTopInventory().getHolder())
                .isInstanceOf(com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder.class);
    }

    @Test
    void clickingACandidateSetsItAsParentAndReturnsToSettings() {
        selector.open(player, viewer, CHILD);

        fireClick(0); // content slot 0 holds the first candidate, "pvp"

        assertThat(categories.lastSaved()).isNotNull();
        assertThat(categories.lastSaved().id()).isEqualTo("child");
        assertThat(categories.lastSaved().parentCategoryId()).contains("pvp");
    }

    @Test
    void clickingNoParentClearsTheParentAndReturnsToSettings() {
        selector.open(player, viewer, CHILD);

        fireClick(NONE_SLOT);

        assertThat(categories.lastSaved()).isNotNull();
        assertThat(categories.lastSaved().id()).isEqualTo("child");
        assertThat(categories.lastSaved().parentCategoryId()).isEmpty();
    }

    @Test
    void clickingBackReturnsToSettingsWithoutSaving() {
        selector.open(player, viewer, CHILD);

        fireClick(BACK_SLOT);

        assertThat(categories.lastSaved()).isNull();
    }

    /**
     * The slot -> (material, plain name) map the bespoke {@code WarpCategoryParentSelectorMenu} produced for this
     * fixture: a DIAMOND for "pvp" at content slot 0, a BOOK for the materialless "misc" at slot 1 (the old fallback),
     * the "no parent" BARRIER at slot 49 and the back ARROW at slot 53, each named through the catalog key the test's
     * {@code KeyMessages} returns verbatim, plus the engine's mandatory ARROW nav at 45 and 46. The category being
     * edited is excluded by the cyclic-parent guard, and the gray-glass filler slots are dropped from the snapshot.
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

    /** A category repository over a fixed list that records the category the selector's assign saves back. */
    private static final class RecordingCategories implements WarpCategoryRepository {
        private final List<WarpCategory> categories;
        private @Nullable WarpCategory saved;

        RecordingCategories(List<WarpCategory> categories) {
            this.categories = new ArrayList<>(categories);
        }

        @Nullable WarpCategory lastSaved() {
            return saved;
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
        }

        @Override
        public void delete(String id) {}
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
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
