package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.menu.WorldEditorTestSupport.Engine;
import com.uxplima.uxmessentials.shared.menu.WorldEditorTestSupport.FakeEngine;
import com.uxplima.uxmessentials.shared.menu.WorldEditorTestSupport.FakeRepository;
import com.uxplima.uxmessentials.shared.menu.WorldEditorTestSupport.Snapshot;
import com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldGenerationMenu;
import com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldMainMenu;
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
 * The world generation-summary golden test: the engine-rendered read-only generation screen must draw the four info
 * slots the original {@code WorldGenerationView} drew. Environment (GRASS_BLOCK at 10), world type (MAP at 12), seed
 * (WHEAT_SEEDS at 14) and external generator (COMMAND_BLOCK at 16), over the ARROW back button at 22. The engine's
 * window is snapshotted as {@code (slot -> material, plain name)} and asserted equal, slot for slot, to the baseline
 * the old view produced. Then, through the engine's own {@code MenuListener}, the back button is clicked to prove the
 * migrated path returns to that world's per-world hub (now a holder-backed engine window). Every key renders verbatim,
 * so a wrong key, material or slot still shows up as a snapshot mismatch.
 */
class WorldGenerationGoldenTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeRepository repository;
    private FakeEngine engine;

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
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameInfoSlotsAsTheOldView() {
        repository.seed("alpha", WorldEnvironment.NORMAL);
        Map<Integer, Snapshot> baseline = oldViewBaseline();

        Map<Integer, Snapshot> engineGrid = WorldEditorTestSupport.snapshot(open());

        assertThat(engineGrid.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engineGrid).isEqualTo(baseline);
    }

    @Test
    void clickingBackThroughTheEngineReturnsToThePerWorldHub() {
        repository.seed("alpha", WorldEnvironment.NORMAL);
        open();
        // Slot 22 is the back button; a click must open that world's per-world hub, a holder-backed engine window whose
        // summary item (MAP at slot 4) the hub draws.
        WorldEditorTestSupport.fireClick(server, player, 22, ClickType.LEFT);

        Inventory hub = player.getOpenInventory().getTopInventory();
        assertThat(hub.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(hub.getItem(4)).isNotNull();
        assertThat(hub.getItem(4).getType()).isEqualTo(Material.MAP);
    }

    /** The slot -> (material, plain name) map the deleted generation view produced for a NORMAL "alpha" world. */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        return Map.of(
                10, new Snapshot(Material.GRASS_BLOCK, "world.editor.gen.environment"),
                12, new Snapshot(Material.MAP, "world.editor.gen.type"),
                14, new Snapshot(Material.WHEAT_SEEDS, "world.editor.gen.seed"),
                16, new Snapshot(Material.COMMAND_BLOCK, "world.editor.gen.generator"),
                22, new Snapshot(Material.ARROW, "world.editor.nav.back"));
    }

    /** Wire the generation screen (bound to a real hub) over the engine and open it for "alpha". */
    private Inventory open() {
        Engine eng = WorldEditorTestSupport.engine(server, plugin, guiText, scheduler);
        WorldMainMenu mainMenu = new WorldMainMenu(eng.menus(), scheduler, repository, engine, (p, v) -> {});
        WorldGenerationMenu generationMenu = new WorldGenerationMenu(eng.menus(), scheduler, repository);
        mainMenu.bind(
                new com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldGridMenu(
                        eng.menus(), scheduler, repository, setProperty()),
                generationMenu,
                (v, w) -> {});
        generationMenu.bind(mainMenu);
        mainMenu.register(eng.bindings(), dataFolder, WorldEditorTestSupport.NOOP);
        generationMenu.register(eng.bindings(), dataFolder, WorldEditorTestSupport.NOOP);
        generationMenu.open(player, viewer, WorldName.of("alpha"));
        return player.getOpenInventory().getTopInventory();
    }

    /** A real SetWorldProperty over the recording repository: only needed to satisfy the grid menu the hub binds. */
    private com.uxplima.uxmessentials.worlds.application.SetWorldProperty setProperty() {
        return new com.uxplima.uxmessentials.worlds.application.SetWorldProperty(
                repository,
                new com.uxplima.uxmessentials.shared.application.message.Notifier(
                        new WorldEditorTestSupport.KeyMessages(), new WorldEditorTestSupport.SilentSink()),
                new WorldEditorTestSupport.RecordingEvents(),
                scheduler);
    }
}
