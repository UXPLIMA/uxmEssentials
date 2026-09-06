package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.menu.WorldEditorTestSupport.Engine;
import com.uxplima.uxmessentials.shared.menu.WorldEditorTestSupport.FakeEngine;
import com.uxplima.uxmessentials.shared.menu.WorldEditorTestSupport.FakeRepository;
import com.uxplima.uxmessentials.shared.menu.WorldEditorTestSupport.Snapshot;
import com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldCreateDraft;
import com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldCreateMenu;
import com.uxplima.uxmessentials.worlds.application.CreateWorld;
import com.uxplima.uxmessentials.worlds.application.WorldEditorMessageKey;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The new-world create-screen golden test: the engine-rendered create screen must draw the field buttons the original
 * {@code WorldCreateView} drew. The NAME_TAG name button at 4, the GRASS_BLOCK environment selector at 10, the MAP
 * type selector at 12, the COMMAND_BLOCK generator selector at 14, the WHEAT_SEEDS seed button at 16, the ARROW back
 * button at 18, and the NETHER_STAR create button at 22. The engine's window is snapshotted as {@code (slot ->
 * material, plain name)} and asserted equal, slot for slot, to that baseline. Then the migrated behaviours are proved
 * through the engine's own {@code MenuListener} and the package-private name seam: a left click on the environment
 * selector cycles it (the selector's value surfaces NORMAL -> NETHER through the create-environment token), the name
 * seam validates and carries a typed name (surfaced through the create-name token), and a left click on the create
 * button (with a named draft) runs the create use case so the world is built and the list re-opens.
 */
class WorldCreateGoldenTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private Scheduler scheduler;
    private FakeRepository repository;
    private FakeEngine engine;
    private TextInput textInput;
    private boolean listReopened;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Admin");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler = new WorldEditorTestSupport.SyncScheduler();
        repository = new FakeRepository();
        engine = new FakeEngine();
        textInput = TextInputTestKit.create(
                plugin,
                new GuiText(new WorldEditorTestSupport.KeyMessages()),
                scheduler,
                Path.of("nonexistent"),
                WorldEditorTestSupport.NOOP);
        listReopened = false;
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameFieldButtonsAsTheOldView() {
        Map<Integer, Snapshot> baseline = oldViewBaseline();

        WorldCreateMenu menu = menu(new WorldEditorTestSupport.KeyMessages());
        menu.open(player, viewer);
        Map<Integer, Snapshot> grid =
                WorldEditorTestSupport.snapshot(player.getOpenInventory().getTopInventory());

        assertThat(grid.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(grid).isEqualTo(baseline);
    }

    @Test
    void clickingTheEnvironmentSelectorCyclesTheDraft() {
        // Surface the environment selector's value so the cycle's NORMAL -> NETHER advance is observable.
        WorldCreateMenu menu = menu(new WorldEditorTestSupport.TokenMessages(
                WorldEditorMessageKey.CREATE_ENVIRONMENT, "world_create_environment"));
        menu.open(player, viewer);
        // Slot 10 is the environment selector; a left click cycles NORMAL -> NETHER and re-opens with the new draft.
        WorldEditorTestSupport.fireClick(server, player, 10, ClickType.LEFT);

        Inventory reopened = player.getOpenInventory().getTopInventory();
        assertThat(reopened.getItem(10)).isNotNull();
        assertThat(reopened.getItem(10).getType()).isEqualTo(Material.GRASS_BLOCK);
        assertThat(WorldEditorTestSupport.plainNameAt(reopened, 10)).isEqualTo("NETHER");
    }

    @Test
    void clickingTheNameButtonOpensTheInputSeam() {
        WorldCreateMenu menu = menu(new WorldEditorTestSupport.KeyMessages());
        menu.open(player, viewer);
        // Slot 4 is the name button; a click opens the anvil/chat input prompt (the seam the name is captured through).
        // The seam's apply path is unit-tested in WorldCreateMenuInputSeamTest; here we only prove the click reaches it
        // by confirming the create window closes as the input prompt opens.
        WorldEditorTestSupport.fireClick(server, player, 4, ClickType.LEFT);

        assertThat(player.getOpenInventory().getTopInventory().getHolder())
                .isNotInstanceOf(com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder.class);
    }

    @Test
    void clickingCreateWithANamedDraftRunsTheCreateUseCase() {
        WorldCreateMenu menu = menu(new WorldEditorTestSupport.KeyMessages());
        menu.open(player, viewer, WorldCreateDraft.empty().withName("built"));
        // Slot 22 is the create button; a click with a named draft builds the world on the global region thread.
        WorldEditorTestSupport.fireClick(server, player, 22, ClickType.LEFT);

        assertThat(repository.exists(WorldName.of("built"))).isTrue();
        assertThat(listReopened).isTrue();
    }

    /** The slot -> (material, plain name) map the deleted create view produced for a fresh (empty) draft. */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        return Map.of(
                4, new Snapshot(Material.NAME_TAG, "world.editor.create.name"),
                10, new Snapshot(Material.GRASS_BLOCK, "world.editor.create.environment"),
                12, new Snapshot(Material.MAP, "world.editor.create.type"),
                14, new Snapshot(Material.COMMAND_BLOCK, "world.editor.create.generator"),
                16, new Snapshot(Material.WHEAT_SEEDS, "world.editor.create.seed"),
                18, new Snapshot(Material.ARROW, "world.editor.nav.back"),
                22, new Snapshot(Material.NETHER_STAR, "world.editor.create.confirm"));
    }

    /** Wire the create screen over the engine with the given catalog, registering its spec so opens and clicks resolve. */
    private WorldCreateMenu menu(Messages messages) {
        Engine eng = WorldEditorTestSupport.engine(server, plugin, new GuiText(messages), scheduler);
        Notifier notifier = new Notifier(messages, new WorldEditorTestSupport.SilentSink());
        CreateWorld createWorld = new CreateWorld(
                repository,
                engine,
                notifier,
                new WorldEditorTestSupport.RecordingEvents(),
                scheduler,
                Clock.systemUTC());
        WorldCreateMenu menu = new WorldCreateMenu(
                eng.menus(), scheduler, createWorld, notifier, textInput, (p, v) -> listReopened = true);
        menu.register(eng.bindings(), dataFolder, WorldEditorTestSupport.NOOP);
        return menu;
    }
}
