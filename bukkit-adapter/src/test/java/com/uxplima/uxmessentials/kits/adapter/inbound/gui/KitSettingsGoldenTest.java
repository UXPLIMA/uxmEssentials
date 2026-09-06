package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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

import com.uxplima.uxmessentials.kits.application.DelKit;
import com.uxplima.uxmessentials.kits.application.KitEditor;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.kits.domain.KitCost;
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
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmessentials.shared.menu.TileText;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The kit settings golden test: the engine-rendered panel must draw the exact property grid the original bespoke
 * {@code KitSettingsView} drew on its own {@code Bukkit.createInventory}, and each button's apply seam must run the
 * same mutation the old click handler did. The fixture is a single "pvp" kit with a permission requirement, a 60s
 * cooldown, a 100 cost, a custom display name, a DIAMOND display material, two lore lines, one command, auto-equip
 * on, and a "combat" category. The panel snapshots as {@code (slot -> material, plain name)} and is asserted equal,
 * slot for slot, to the analytic baseline the old window produced: a CHEST edit-items at 0, the PAPER permission
 * toggle at 2 (PAPER because the kit requires a permission), a CLOCK one-time at 4, a COMPARATOR cooldown at 6, a
 * GOLD_INGOT cost at 8, a NAME_TAG display-name at 10, the kit's own DIAMOND display icon at 12, a BOOK display-lore
 * at 14, a COMMAND_BLOCK commands at 16, a FEATHER first-join at 18, an ARMOR_STAND auto-equip at 20, a
 * REDSTONE_BLOCK delete at 22, a BOOKSHELF category at 24, and an ARROW back at 26.
 *
 * <p>The apply seams are driven directly (MockBukkit cannot drive a live anvil): {@code applyCooldown} /
 * {@code applyCost} / {@code applyDisplayName} / {@code applyDisplayLore} / {@code applyCommands} save the
 * single-field copy the old prompts saved and re-open the panel. The toggle, display-material, delete, category, and
 * back buttons are fired as real clicks through the engine's own
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener}. So the move is faithful in
 * both appearance and behaviour.
 */
class KitSettingsGoldenTest {

    private static final int EDIT_ITEMS_SLOT = 0;
    private static final int PERMISSION_SLOT = 2;
    private static final int ONETIME_SLOT = 4;
    private static final int COOLDOWN_SLOT = 6;
    private static final int COST_SLOT = 8;
    private static final int DISPLAY_NAME_SLOT = 10;
    private static final int DISPLAY_MATERIAL_SLOT = 12;
    private static final int DISPLAY_LORE_SLOT = 14;
    private static final int COMMANDS_SLOT = 16;
    private static final int FIRSTJOIN_SLOT = 18;
    private static final int AUTOEQUIP_SLOT = 20;
    private static final int DELETE_SLOT = 22;
    private static final int CATEGORY_SLOT = 24;
    private static final int BACK_SLOT = 26;

    private static final KitId PVP_ID = KitId.of("pvp");

    private static final KitDefinition PVP = KitDefinition.repeatable(PVP_ID, List.of(), Duration.ofSeconds(60))
            .withPermission(true)
            .withCost(KitCost.of(new BigDecimal("100")))
            .withDisplayName(Optional.of("<red>PvP</red>"))
            .withDisplayMaterial(Optional.of("DIAMOND"))
            .withDisplayLore(List.of("one", "two"))
            .withCommands(List.of("console:say hi"))
            .withAutoEquip(true)
            .withCategoryId(Optional.of("combat"));

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private TestMenuEngine engine;
    private RecordingRepository repository;
    private KitSettingsView settings;
    private AtomicReference<PlayerRef> backTarget;

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
        repository = new RecordingRepository(List.of(PVP));
        backTarget = new AtomicReference<>();
        Notifier notifier = new Notifier(new KeyMessages(), new NoSink());
        KitEditor kitEditor = new KitEditor(repository, notifier);
        DelKit delKit = new DelKit(repository, notifier);
        KitEditorView editorView = new KitEditorView(new KeyMessages(), kitEditor, scheduler);
        TextInput textInput = org.mockito.Mockito.mock(TextInput.class);
        settings = new KitSettingsView(
                engine.menus(),
                guiText,
                new KeyMessages(),
                textInput,
                kitEditor,
                delKit,
                editorView,
                (p, v) -> backTarget.set(v));
        // The category button opens the engine category selector; binding a real one keeps the panel whole, but the
        // category assign itself is proven by the category-selector golden test, so this test exercises the other
        // buttons.
        KitCategorySelectorMenu categorySelector = new KitCategorySelectorMenu(
                engine.menus(), new KeyMessages(), scheduler, new EmptyCategories(), kitEditor, settings);
        categorySelector.register(engine.bindings(), dataFolder, NOOP);
        settings.bind(categorySelector);
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
    void clickingThePermissionButtonFlipsTheFlagAndRecordsTheSave() {
        settings.open(player, viewer, PVP);

        fireClick(PERMISSION_SLOT);

        assertThat(repository.lastSaved())
                .hasValueSatisfying(saved -> assertThat(saved.permission()).isFalse());
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void clickingTheAutoEquipButtonFlipsTheFlag() {
        settings.open(player, viewer, PVP);

        fireClick(AUTOEQUIP_SLOT);

        assertThat(repository.lastSaved())
                .hasValueSatisfying(saved -> assertThat(saved.autoEquip()).isFalse());
    }

    @Test
    void applyingAValidCooldownSavesItAndReopensThePanel() {
        settings.open(player, viewer, PVP);

        settings.applyCooldown(player, viewer, PVP, "120");

        assertThat(repository.lastSaved())
                .hasValueSatisfying(saved -> assertThat(saved.cooldownSeconds()).isEqualTo(120));
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void applyingANonNumberCooldownIsRejectedWithoutSaving() {
        settings.open(player, viewer, PVP);

        settings.applyCooldown(player, viewer, PVP, "soon");

        assertThat(repository.lastSaved()).isEmpty();
    }

    @Test
    void applyingANegativeCooldownIsRejectedWithoutSaving() {
        settings.open(player, viewer, PVP);

        settings.applyCooldown(player, viewer, PVP, "-5");

        assertThat(repository.lastSaved()).isEmpty();
    }

    @Test
    void applyingAValidCostSavesIt() {
        settings.open(player, viewer, PVP);

        settings.applyCost(player, viewer, PVP, "250.50");

        assertThat(repository.lastSaved())
                .hasValueSatisfying(saved -> assertThat(saved.cost().amount()).isEqualByComparingTo("250.50"));
    }

    @Test
    void applyingFreeAsCostClearsTheCost() {
        settings.open(player, viewer, PVP);

        settings.applyCost(player, viewer, PVP, "free");

        assertThat(repository.lastSaved())
                .hasValueSatisfying(saved -> assertThat(saved.hasCost()).isFalse());
    }

    @Test
    void applyingANonNumberCostIsRejectedWithoutSaving() {
        settings.open(player, viewer, PVP);

        settings.applyCost(player, viewer, PVP, "lots");

        assertThat(repository.lastSaved()).isEmpty();
    }

    @Test
    void applyingADisplayNameSavesIt() {
        settings.open(player, viewer, PVP);

        settings.applyDisplayName(player, viewer, PVP, "<gold>Arena</gold>");

        assertThat(repository.lastSaved())
                .hasValueSatisfying(saved -> assertThat(saved.display().name()).contains("<gold>Arena</gold>"));
    }

    @Test
    void applyingNoneAsDisplayNameClearsIt() {
        settings.open(player, viewer, PVP);

        settings.applyDisplayName(player, viewer, PVP, "none");

        assertThat(repository.lastSaved())
                .hasValueSatisfying(saved -> assertThat(saved.display().name()).isEmpty());
    }

    @Test
    void applyingDisplayLoreSavesThePipeSplitLines() {
        settings.open(player, viewer, PVP);

        settings.applyDisplayLore(player, viewer, PVP, "a|b|c");

        assertThat(repository.lastSaved())
                .hasValueSatisfying(saved -> assertThat(saved.display().lore()).containsExactly("a", "b", "c"));
    }

    @Test
    void applyingCommandsSavesThePipeSplitCommands() {
        settings.open(player, viewer, PVP);

        settings.applyCommands(player, viewer, PVP, "console:say a|player:warp spawn");

        assertThat(repository.lastSaved())
                .hasValueSatisfying(
                        saved -> assertThat(saved.commands()).containsExactly("console:say a", "player:warp spawn"));
    }

    @Test
    void clickingTheDisplayMaterialButtonCopiesTheMainHandItem() {
        settings.open(player, viewer, PVP);
        player.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));

        fireClick(DISPLAY_MATERIAL_SLOT);

        assertThat(repository.lastSaved())
                .hasValueSatisfying(
                        saved -> assertThat(saved.display().material()).contains("NETHERITE_SWORD"));
    }

    @Test
    void clickingDeleteRemovesTheKitAndReturnsToTheManager() {
        settings.open(player, viewer, PVP);

        fireClick(DELETE_SLOT);

        assertThat(repository.find(PVP_ID)).isEmpty();
        assertThat(backTarget.get()).isEqualTo(viewer);
    }

    @Test
    void clickingBackInvokesTheBackSeam() {
        settings.open(player, viewer, PVP);

        fireClick(BACK_SLOT);

        assertThat(backTarget.get()).isEqualTo(viewer);
    }

    @Test
    void clickingTheCategoryButtonOpensTheEngineSelector() {
        settings.open(player, viewer, PVP);

        fireClick(CATEGORY_SLOT);

        // The category selector is an engine list window; opening it leaves a menu-backed inventory in place.
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    /**
     * The slot -> (material, plain name) map the bespoke {@code KitSettingsView} produced for the "pvp" fixture: a
     * CHEST edit-items at slot 0, a PAPER permission toggle at 2 (PAPER because the kit requires a permission), a
     * CLOCK one-time at 4, a COMPARATOR cooldown at 6, a GOLD_INGOT cost at 8, a NAME_TAG display-name at 10, the
     * kit's configured DIAMOND icon at the display-material button (12), a BOOK display-lore at 14, a COMMAND_BLOCK
     * commands at 16, a FEATHER first-join at 18, an ARMOR_STAND auto-equip at 20, a REDSTONE_BLOCK delete at 22, a
     * BOOKSHELF category at 24, and an ARROW back at 26, each named through the catalog key the test's
     * {@code KeyMessages} returns verbatim. The gray-glass filler slots are dropped from the snapshot.
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(
                EDIT_ITEMS_SLOT,
                new Snapshot(Material.CHEST, KitsMessageKey.KIT_EDITOR_SETTINGS_EDIT_ITEMS_NAME.key()));
        baseline.put(
                PERMISSION_SLOT,
                new Snapshot(Material.PAPER, KitsMessageKey.KIT_EDITOR_SETTINGS_PERMISSION_NAME.key()));
        baseline.put(ONETIME_SLOT, new Snapshot(Material.CLOCK, KitsMessageKey.KIT_EDITOR_SETTINGS_ONETIME_NAME.key()));
        baseline.put(
                COOLDOWN_SLOT,
                new Snapshot(Material.COMPARATOR, KitsMessageKey.KIT_EDITOR_SETTINGS_COOLDOWN_NAME.key()));
        baseline.put(COST_SLOT, new Snapshot(Material.GOLD_INGOT, KitsMessageKey.KIT_EDITOR_SETTINGS_COST_NAME.key()));
        baseline.put(
                DISPLAY_NAME_SLOT,
                new Snapshot(Material.NAME_TAG, KitsMessageKey.KIT_EDITOR_SETTINGS_DISPLAY_NAME_NAME.key()));
        baseline.put(
                DISPLAY_MATERIAL_SLOT,
                new Snapshot(Material.DIAMOND, KitsMessageKey.KIT_EDITOR_SETTINGS_DISPLAY_MATERIAL_NAME.key()));
        baseline.put(
                DISPLAY_LORE_SLOT,
                new Snapshot(Material.BOOK, KitsMessageKey.KIT_EDITOR_SETTINGS_DISPLAY_LORE_NAME.key()));
        baseline.put(
                COMMANDS_SLOT,
                new Snapshot(Material.COMMAND_BLOCK, KitsMessageKey.KIT_EDITOR_SETTINGS_COMMANDS_NAME.key()));
        baseline.put(
                FIRSTJOIN_SLOT,
                new Snapshot(Material.FEATHER, KitsMessageKey.KIT_EDITOR_SETTINGS_FIRSTJOIN_NAME.key()));
        baseline.put(
                AUTOEQUIP_SLOT,
                new Snapshot(Material.ARMOR_STAND, KitsMessageKey.KIT_EDITOR_SETTINGS_AUTOEQUIP_NAME.key()));
        baseline.put(
                DELETE_SLOT,
                new Snapshot(Material.REDSTONE_BLOCK, KitsMessageKey.KIT_EDITOR_SETTINGS_DELETE_NAME.key()));
        baseline.put(
                CATEGORY_SLOT,
                new Snapshot(Material.BOOKSHELF, KitsMessageKey.KIT_EDITOR_SETTINGS_CATEGORY_NAME.key()));
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

    /** A kit repository over a fixed, mutable list that records the definition the save path stores. */
    private static final class RecordingRepository implements KitRepository {
        private final List<KitDefinition> kits;
        private @Nullable KitDefinition saved;

        RecordingRepository(List<KitDefinition> kits) {
            this.kits = new ArrayList<>(kits);
        }

        /** The last definition {@link #save} stored, or empty if none was saved: read by the apply-seam assertions. */
        Optional<KitDefinition> lastSaved() {
            return Optional.ofNullable(saved);
        }

        @Override
        public Optional<KitDefinition> find(KitId id) {
            return kits.stream().filter(k -> k.id().equals(id)).findFirst();
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
            this.saved = definition;
            kits.removeIf(k -> k.id().equals(definition.id()));
            kits.add(definition);
        }

        @Override
        public void delete(KitId id) {
            kits.removeIf(k -> k.id().equals(id));
        }
    }

    /** An empty category repository: the category button is exercised only for the window it opens, not its picks. */
    private static final class EmptyCategories implements KitCategoryRepository {
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

    private static final class NoSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
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
