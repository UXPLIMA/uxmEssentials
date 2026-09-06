package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.menu.WorldEditorTestSupport.Engine;
import com.uxplima.uxmessentials.shared.menu.WorldEditorTestSupport.FakeEngine;
import com.uxplima.uxmessentials.shared.menu.WorldEditorTestSupport.FakeRepository;
import com.uxplima.uxmessentials.shared.menu.WorldEditorTestSupport.Snapshot;
import com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldGenerationMenu;
import com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldGridMenu;
import com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldMainMenu;
import com.uxplima.uxmessentials.worlds.application.SetWorldProperty;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The per-world hub golden test: the engine-rendered editor hub must draw the navigation panel the original
 * {@code WorldMainView} drew. The MAP summary at 4, the DIAMOND_SWORD rules button at 11, the GRASS_BLOCK generation
 * button at 13, the IRON_DOOR access button at 15, the ARROW back button at 18, and the load/unload toggle at 22
 * (LIME_DYE while loaded). The engine's window is snapshotted as {@code (slot -> material, plain name)} and asserted
 * equal, slot for slot, to that baseline. Then, through the engine's own {@code MenuListener}, the three drill buttons
 * are clicked to prove each opens the right sub-screen (rules and access open the shared grid with the right title;
 * generation opens the read-only summary), and the back button is clicked to prove it reopens the engine world picker
 * through the reopen seam. Every key renders verbatim, so a wrong key, material or slot shows up as a mismatch.
 */
class WorldMainGoldenTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeRepository repository;
    private FakeEngine engine;
    private final AtomicBoolean listReopened = new AtomicBoolean(false);

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Admin");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new WorldEditorTestSupport.KeyMessages());
        scheduler = new WorldEditorTestSupport.SyncScheduler();
        repository = new FakeRepository();
        engine = new FakeEngine();
        listReopened.set(false);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameNavigationPanelAsTheOldView() {
        repository.seed("alpha", WorldEnvironment.NORMAL);
        Map<Integer, Snapshot> baseline = oldViewBaseline();

        Map<Integer, Snapshot> engineGrid = WorldEditorTestSupport.snapshot(open());

        assertThat(engineGrid.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engineGrid).isEqualTo(baseline);
    }

    @Test
    void clickingTheRulesButtonOpensTheGridShowingTheRulesSet() {
        repository.seed("alpha", WorldEnvironment.NORMAL);
        open();
        WorldEditorTestSupport.fireClick(server, player, 11, ClickType.LEFT);

        Inventory grid = player.getOpenInventory().getTopInventory();
        assertThat(grid.getHolder()).isInstanceOf(MenuHolder.class);
        // The rules set leads with PvP (DIAMOND_SWORD) at content slot 0; the access set would lead with IRON_DOOR.
        assertThat(grid.getItem(0)).isNotNull();
        assertThat(grid.getItem(0).getType()).isEqualTo(Material.DIAMOND_SWORD);
    }

    @Test
    void clickingTheAccessButtonOpensTheGridShowingTheAccessSet() {
        repository.seed("alpha", WorldEnvironment.NORMAL);
        open();
        WorldEditorTestSupport.fireClick(server, player, 15, ClickType.LEFT);

        Inventory grid = player.getOpenInventory().getTopInventory();
        assertThat(grid.getHolder()).isInstanceOf(MenuHolder.class);
        // The access set leads with the restricted-access toggle (IRON_DOOR) at content slot 0.
        assertThat(grid.getItem(0)).isNotNull();
        assertThat(grid.getItem(0).getType()).isEqualTo(Material.IRON_DOOR);
    }

    @Test
    void clickingTheGenerationButtonOpensTheReadOnlySummary() {
        repository.seed("alpha", WorldEnvironment.NORMAL);
        open();
        WorldEditorTestSupport.fireClick(server, player, 13, ClickType.LEFT);

        Inventory gen = player.getOpenInventory().getTopInventory();
        assertThat(gen.getHolder()).isInstanceOf(MenuHolder.class);
        // The generation summary draws its environment info (GRASS_BLOCK) at slot 10.
        assertThat(gen.getItem(10)).isNotNull();
        assertThat(gen.getItem(10).getType()).isEqualTo(Material.GRASS_BLOCK);
    }

    @Test
    void clickingBackReopensTheEngineWorldPicker() {
        repository.seed("alpha", WorldEnvironment.NORMAL);
        open();
        WorldEditorTestSupport.fireClick(server, player, 18, ClickType.LEFT);

        assertThat(listReopened).isTrue();
    }

    /** The slot -> (material, plain name) map the deleted main view produced for a loaded NORMAL "alpha" world. */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        return Map.of(
                4, new Snapshot(Material.MAP, "world.editor.main.summary-name"),
                11, new Snapshot(Material.DIAMOND_SWORD, "world.editor.nav.rules"),
                13, new Snapshot(Material.GRASS_BLOCK, "world.editor.nav.generation"),
                15, new Snapshot(Material.IRON_DOOR, "world.editor.nav.access"),
                18, new Snapshot(Material.ARROW, "world.editor.nav.back"),
                22, new Snapshot(Material.LIME_DYE, "world.editor.nav.unload"));
    }

    /** Wire the whole editor (hub + grid + generation) over the engine and open the hub for "alpha". */
    private Inventory open() {
        Engine eng = WorldEditorTestSupport.engine(server, plugin, guiText, scheduler);
        WorldMainMenu mainMenu =
                new WorldMainMenu(eng.menus(), scheduler, repository, engine, (p, v) -> listReopened.set(true));
        WorldGenerationMenu generationMenu = new WorldGenerationMenu(eng.menus(), scheduler, repository);
        WorldGridMenu gridMenu = new WorldGridMenu(eng.menus(), scheduler, repository, setProperty());
        mainMenu.bind(gridMenu, generationMenu, (v, w) -> {});
        generationMenu.bind(mainMenu);
        gridMenu.bind(mainMenu);
        mainMenu.register(eng.bindings(), dataFolder, WorldEditorTestSupport.NOOP);
        generationMenu.register(eng.bindings(), dataFolder, WorldEditorTestSupport.NOOP);
        gridMenu.register(eng.bindings(), dataFolder, WorldEditorTestSupport.NOOP);
        mainMenu.open(player, viewer, WorldName.of("alpha"));
        return player.getOpenInventory().getTopInventory();
    }

    private SetWorldProperty setProperty() {
        return new SetWorldProperty(
                repository,
                new Notifier(new WorldEditorTestSupport.KeyMessages(), new WorldEditorTestSupport.SilentSink()),
                new WorldEditorTestSupport.RecordingEvents(),
                scheduler);
    }
}
