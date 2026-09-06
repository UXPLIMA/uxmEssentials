package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
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

import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategorySelectorMenu;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorView;
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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
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
 * The kit→category selector golden test: the engine-rendered selector must draw the exact grid the original bespoke
 * {@code KitCategorySelectorMenu} drew on its own {@code Bukkit.createInventory}. The fixture is two categories (a
 * {@code DIAMOND}-iconed "pvp" and a default-BOOK "misc") over the selector's six-row layout (content slots 0..44,
 * gray-glass filler), so page 0 places one icon per category at content slots 0 and 1, the "No Category" BARRIER at
 * slot 49, and the back ARROW at slot 53. The engine window is snapshotted as {@code (slot -> material, plain name)}
 * and asserted equal, slot for slot, to the analytic baseline the old view produced. Category icons, the two fixed
 * buttons, and the engine's mandatory nav arrows at 45/46. Then a left click on the first category through the
 * engine's own {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener} proves the
 * migrated path runs the same assign the old click did, the kit saved with that category id, and returns the viewer
 * to its settings window, faithful in both appearance and behaviour.
 */
class KitCategorySelectorGoldenTest {

    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 46;
    private static final int NONE_SLOT = 49;
    private static final int BACK_SLOT = 53;

    private static final KitCategory PVP =
            new KitCategory("pvp", "<red>PvP</red>", Optional.of("DIAMOND"), List.of(), 0, Optional.empty());
    private static final KitCategory MISC =
            new KitCategory("misc", "<gray>Misc</gray>", Optional.empty(), List.of(), 0, Optional.empty());

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private TestMenuEngine engine;
    private RecordingRepository kits;
    private KitCategorySelectorMenu selector;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        kits = new RecordingRepository();
        KitEditor editor = new KitEditor(kits, new Notifier(new KeyMessages(), new NoSink()));
        KitCategoryRepository categories = new FixedCategories(List.of(PVP, MISC));
        // The selector reopens the per-kit settings panel after a pick; that panel renders through the engine now, so
        // it is built over the same engine and its spec registered. The assign assertions then see a menu-backed
        // window.
        KitSettingsView settingsView = new KitSettingsView(
                engine.menus(),
                guiText,
                new KeyMessages(),
                org.mockito.Mockito.mock(TextInput.class),
                editor,
                new DelKit(kits, new Notifier(new KeyMessages(), new NoSink())),
                new KitEditorView(new KeyMessages(), editor, scheduler),
                (p, v) -> {});
        settingsView.register(engine.bindings(), dataFolder, NOOP);
        selector = new KitCategorySelectorMenu(
                engine.menus(), new KeyMessages(), scheduler, categories, editor, settingsView);
        selector.register(engine.bindings(), dataFolder, NOOP);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameCategoryGridButtonsAndFillerAsTheOldView() {
        selector.open(player, viewer, kits.starter());

        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> rendered = snapshot(player.getOpenInventory().getTopInventory());

        assertThat(rendered.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(rendered).isEqualTo(baseline);
    }

    @Test
    void theEngineWindowIsMenuBacked() {
        selector.open(player, viewer, kits.starter());
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void clickingACategoryAssignsItToTheKitAndReturnsToSettings() {
        selector.open(player, viewer, kits.starter());

        fireClick(0); // content slot 0 holds "pvp"

        assertThat(kits.lastSaved()).isNotNull();
        assertThat(kits.lastSaved().categoryId()).contains("pvp");
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void clickingNoCategoryClearsTheKitsCategoryAndReturnsToSettings() {
        selector.open(player, viewer, kits.starter());

        fireClick(NONE_SLOT);

        assertThat(kits.lastSaved()).isNotNull();
        assertThat(kits.lastSaved().categoryId()).isEmpty();
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void clickingBackReturnsToSettingsWithoutSaving() {
        selector.open(player, viewer, kits.starter());

        fireClick(BACK_SLOT);

        assertThat(kits.lastSaved()).isNull();
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    /**
     * The slot -> (material, plain name) map the bespoke {@code KitCategorySelectorMenu} produced for this fixture: a
     * DIAMOND for "pvp" at content slot 0, a BOOK for the materialless "misc" at slot 1 (the old fallback), the "No
     * Category" BARRIER at slot 49 and the back ARROW at slot 53, each named through the catalog key the test's
     * {@code KeyMessages} returns verbatim, plus the engine's mandatory ARROW nav at 45 and 46. The gray-glass filler
     * slots are dropped from the snapshot, so a wrong material, name, or misplaced icon still shows up as a mismatch.
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.DIAMOND, "PvP"));
        baseline.put(1, new Snapshot(Material.BOOK, "Misc"));
        baseline.put(PREV_SLOT, new Snapshot(Material.ARROW, KitsMessageKey.KIT_MENU_PREV.key()));
        baseline.put(NEXT_SLOT, new Snapshot(Material.ARROW, KitsMessageKey.KIT_MENU_NEXT.key()));
        baseline.put(
                NONE_SLOT, new Snapshot(Material.BARRIER, KitsMessageKey.KIT_EDITOR_CATEGORY_SELECTOR_NONE_NAME.key()));
        baseline.put(BACK_SLOT, new Snapshot(Material.ARROW, KitsMessageKey.KIT_EDITOR_SETTINGS_BACK_BUTTON.key()));
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

    /** A repository holding one {@code starter} kit and recording the definition the selector's assign saves back. */
    private static final class RecordingRepository implements KitRepository {
        private final KitDefinition starter = KitDefinition.repeatable(KitId.of("starter"), List.of(), Duration.ZERO);
        private KitDefinition saved;

        KitDefinition starter() {
            return starter;
        }

        KitDefinition lastSaved() {
            return saved;
        }

        @Override
        public Optional<KitDefinition> find(KitId id) {
            return id.equals(starter.id()) ? Optional.of(starter) : Optional.empty();
        }

        @Override
        public List<KitDefinition> all() {
            return List.of(starter);
        }

        @Override
        public boolean exists(KitId id) {
            return id.equals(starter.id());
        }

        @Override
        public void save(KitDefinition definition) {
            this.saved = definition;
        }

        @Override
        public void delete(KitId id) {}
    }

    /** A category repository over a fixed, in-hand list: no Bukkit read, the snapshot the selector grids. */
    private record FixedCategories(List<KitCategory> categories) implements KitCategoryRepository {
        @Override
        public Optional<KitCategory> find(String id) {
            return categories.stream().filter(c -> c.id().equals(id)).findFirst();
        }

        @Override
        public List<KitCategory> all() {
            return categories;
        }

        @Override
        public void save(KitCategory category) {}

        @Override
        public void delete(String id) {}
    }

    private static final class NoSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
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
