package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
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
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The warp-category settings golden test: the engine-rendered panel must draw the exact property grid the original
 * bespoke {@code WarpCategorySettingsView} drew on its own {@code Bukkit.createInventory}, and each button's apply
 * seam must run the same mutation the old click handler did. The fixture is a single {@code DIAMOND}-iconed "pvp"
 * category. The panel snapshots as {@code (slot -> material, plain name)} and is asserted equal, slot for slot, to
 * the analytic baseline the old window produced: NAME_TAG name button at slot 10, the category's own DIAMOND icon at
 * slot 12 (the material button shows the configured icon), BOOK lore at 14, COMPARATOR slot at 16, BOOKSHELF parent
 * at 18, REDSTONE_BLOCK delete at 22, ARROW back at 26.
 *
 * <p>The apply seams are driven directly (MockBukkit cannot drive a live anvil): {@code applyName} / {@code applyLore}
 * / {@code applySlot} save the single-field copy the old prompts saved and re-open the panel; the material button is
 * fired as a real click with an item in the main hand; delete and back are fired as real clicks through the engine's
 * own {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener}. So the move is faithful
 * in both appearance and behaviour.
 */
class WarpCategorySettingsGoldenTest {

    private static final int NAME_SLOT = 10;
    private static final int MATERIAL_SLOT = 12;
    private static final int LORE_SLOT = 14;
    private static final int SLOT_SLOT = 16;
    private static final int PARENT_SLOT = 18;
    private static final int DELETE_SLOT = 22;
    private static final int BACK_SLOT = 26;

    private static final WarpCategory PVP =
            new WarpCategory("pvp", "<red>PvP</red>", Optional.of("DIAMOND"), List.of("a", "b"), 5, Optional.empty());

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private Scheduler scheduler;
    private TestMenuEngine engine;
    private RecordingCategories categories;
    private WarpCategorySettingsView settings;
    private AtomicReference<PlayerRef> backTarget;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler = new SyncScheduler();
        engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        categories = new RecordingCategories(List.of(PVP));
        backTarget = new AtomicReference<>();
        TextInput textInput = org.mockito.Mockito.mock(TextInput.class);
        settings = new WarpCategorySettingsView(
                engine.menus(), new KeyMessages(), textInput, categories, (p, v) -> backTarget.set(v));
        // The parent button opens the engine parent selector; binding a real one keeps the panel whole, but the parent
        // assign itself is proven by the parent-selector golden test, so this test exercises the other six buttons.
        WarpCategoryParentSelectorMenu parentSelector =
                new WarpCategoryParentSelectorMenu(engine.menus(), new KeyMessages(), scheduler, categories, settings);
        settings.bind(parentSelector);
        settings.register(engine.bindings(), dataFolder, NOOP);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSamePropertyPanelAsTheOldView() {
        settings.open(player, viewer, PVP);

        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> rendered = snapshot(player.getOpenInventory().getTopInventory());

        assertThat(rendered.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(rendered).isEqualTo(baseline);
    }

    @Test
    void theEngineWindowIsMenuBacked() {
        settings.open(player, viewer, PVP);
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void applyingANameSavesThatNameAndReopensThePanel() {
        settings.open(player, viewer, PVP);

        settings.applyName(player, viewer, PVP, "<gold>Combat</gold>");

        assertThat(categories.lastSaved()).hasValueSatisfying(saved -> {
            assertThat(saved.displayName()).isEqualTo("<gold>Combat</gold>");
            assertThat(saved.id()).isEqualTo("pvp");
        });
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void applyingLoreSavesThePipeSplitLinesAndReopensThePanel() {
        settings.open(player, viewer, PVP);

        settings.applyLore(player, viewer, PVP, "one|two|three");

        assertThat(categories.lastSaved())
                .hasValueSatisfying(saved -> assertThat(saved.displayLore()).containsExactly("one", "two", "three"));
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void applyingNoneAsLoreClearsTheLore() {
        settings.open(player, viewer, PVP);

        settings.applyLore(player, viewer, PVP, "none");

        assertThat(categories.lastSaved())
                .hasValueSatisfying(saved -> assertThat(saved.displayLore()).isEmpty());
    }

    @Test
    void applyingAValidSlotSavesIt() {
        settings.open(player, viewer, PVP);

        settings.applySlot(player, viewer, PVP, "12");

        assertThat(categories.lastSaved())
                .hasValueSatisfying(saved -> assertThat(saved.slot()).isEqualTo(12));
    }

    @Test
    void applyingANonNumberSlotIsRejectedWithoutSaving() {
        settings.open(player, viewer, PVP);

        settings.applySlot(player, viewer, PVP, "abc");

        assertThat(categories.lastSaved()).isEmpty();
    }

    @Test
    void clickingTheMaterialButtonCopiesTheMainHandItem() {
        settings.open(player, viewer, PVP);
        player.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));

        fireClick(MATERIAL_SLOT);

        assertThat(categories.lastSaved())
                .hasValueSatisfying(saved -> assertThat(saved.displayMaterial()).contains("NETHERITE_SWORD"));
    }

    @Test
    void clickingDeleteRemovesTheCategoryAndReturnsToTheManager() {
        settings.open(player, viewer, PVP);

        fireClick(DELETE_SLOT);

        assertThat(categories.find("pvp")).isEmpty();
        assertThat(backTarget.get()).isEqualTo(viewer);
    }

    @Test
    void clickingBackInvokesTheBackSeam() {
        settings.open(player, viewer, PVP);

        fireClick(BACK_SLOT);

        assertThat(backTarget.get()).isEqualTo(viewer);
    }

    /**
     * The slot -> (material, plain name) map the bespoke {@code WarpCategorySettingsView} produced for the "pvp"
     * fixture: a NAME_TAG name button at slot 10, the category's configured DIAMOND icon at the material button (slot
     * 12), a BOOK lore button at 14, a COMPARATOR slot button at 16, a BOOKSHELF parent button at 18, a REDSTONE_BLOCK
     * delete button at 22, and an ARROW back button at 26, each named through the catalog key the test's
     * {@code KeyMessages} returns verbatim. The gray-glass filler slots are dropped from the snapshot.
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(
                NAME_SLOT,
                new Snapshot(Material.NAME_TAG, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_NAME_NAME.key()));
        baseline.put(
                MATERIAL_SLOT,
                new Snapshot(
                        Material.DIAMOND, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_MATERIAL_NAME.key()));
        baseline.put(
                LORE_SLOT,
                new Snapshot(Material.BOOK, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_LORE_NAME.key()));
        baseline.put(
                SLOT_SLOT,
                new Snapshot(Material.COMPARATOR, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_SLOT_NAME.key()));
        baseline.put(
                PARENT_SLOT,
                new Snapshot(Material.BOOKSHELF, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_PARENT_NAME.key()));
        baseline.put(
                DELETE_SLOT,
                new Snapshot(Material.REDSTONE_BLOCK, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_DELETE_NAME.key()));
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

    /** A category repository over a fixed, mutable list that records the category the save path stores. */
    private static final class RecordingCategories implements WarpCategoryRepository {
        private final List<WarpCategory> categories;
        private @Nullable WarpCategory saved;

        RecordingCategories(List<WarpCategory> categories) {
            this.categories = new ArrayList<>(categories);
        }

        /** The last category {@link #save} stored, or empty if none was saved: read by the apply-seam assertions. */
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
