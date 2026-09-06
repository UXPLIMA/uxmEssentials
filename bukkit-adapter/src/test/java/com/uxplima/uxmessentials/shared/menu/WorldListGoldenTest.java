package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldCreateMenu;
import com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldListMenu;
import com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldMainMenu;
import com.uxplima.uxmessentials.worlds.application.CreateWorld;
import com.uxplima.uxmessentials.worlds.application.WorldAccessPolicy;
import com.uxplima.uxmessentials.worlds.application.WorldEditorMessageKey;
import com.uxplima.uxmessentials.worlds.application.WorldTeleportService;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldEntryFee;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.application.port.WorldTeleporter;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import com.uxplima.uxmessentials.worlds.domain.WorldTeleportCause;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The worlds golden test: the engine-rendered {@code /worlds editor} world picker must draw the exact grid the
 * original {@code WorldListView} drew. The store holds two managed worlds ("alpha" normal, "beta" nether), so the
 * list draws a GRASS_BLOCK and a NETHERRACK icon (content slots 0 and 1. The environment icon each world resolves
 * to), the NETHER_STAR create button (slot 49), and the two ARROW nav buttons (slots 48 and 50). The engine's window
 * is snapshotted as {@code (slot -> material, plain name)} and asserted equal, slot for slot, to the baseline the old
 * view produced. Captured once while both rendered the same fixture, then frozen here as the contract so the old
 * class could be deleted. Then, through the engine's own {@link MenuListener}, a left click on the first world icon
 * proves the migrated path opens that world's engine {@link WorldMainMenu} hub, and a right click proves it runs the
 * forced teleport, so the move is faithful in both appearance and behaviour, including the picker's distinct
 * left-edits / right-teleports gesture split.
 *
 * <p>The {@code KeyMessages} catalog surfaces the entry name's {@code world_name} token, so a world's name appears in
 * the rendered label; every other key renders verbatim. A real rendering difference (a wrong key, a wrong material, a
 * misplaced cell, a lost name token) therefore still shows up as a snapshot mismatch.
 */
class WorldListGoldenTest {

    private static final WorldRef WORLD_REF = new WorldRef(UUID.randomUUID(), "alpha");
    private static final Position SPAWN = Position.of(WORLD_REF, 0, 64, 0);

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeWorldRepository repository;
    private FakeWorldEngine engine;
    private RecordingTeleporter teleporter;
    private WorldTeleportService worldTeleport;
    private TextInput textInput;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        player = server.addPlayer("Owner");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        repository = new FakeWorldRepository();
        engine = new FakeWorldEngine();
        teleporter = new RecordingTeleporter();
        worldTeleport = assembleTeleport();
        textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameIconGridCreateAndNavAsTheOldView() {
        seed("alpha", WorldEnvironment.NORMAL);
        seed("beta", WorldEnvironment.NETHER);
        Map<Integer, Snapshot> baseline = oldViewBaseline();

        Map<Integer, Snapshot> engineGrid = snapshotEngine();

        assertThat(engineGrid.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engineGrid).isEqualTo(baseline);
    }

    @Test
    void leftClickingAWorldThroughTheEngineOpensThatWorldsMainHub() {
        seed("alpha", WorldEnvironment.NORMAL);
        openEngine();
        // Content slot 0 is the first world icon, "alpha"; a left click must open that world's engine main editor hub,
        // which is now a holder-backed MenuHolder rather than the old bespoke holder.
        fireClick(0, ClickType.LEFT);

        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void rightClickingAWorldThroughTheEngineRunsTheForcedTeleport() {
        seed("alpha", WorldEnvironment.NORMAL);
        openEngine();
        // A right click on the first world icon must forced-teleport the viewer to that world.
        fireClick(0, ClickType.RIGHT);

        assertThat(teleporter.causes).containsExactly(WorldTeleportCause.ADMIN);
        assertThat(teleporter.targets).containsExactly(viewer);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code WorldListView} produced for this fixture (two managed
     * worlds, "alpha" normal and "beta" nether), captured once while both paths rendered it identically and frozen
     * here as the contract: a GRASS_BLOCK and a NETHERRACK icon (content slots 0 and 1. The names surface through the
     * {@code world_name} token), the NETHER_STAR create button (slot 49), and the two nav ARROWs (slots 48 and 50).
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.GRASS_BLOCK, "alpha"));
        baseline.put(1, new Snapshot(Material.NETHERRACK, "beta"));
        baseline.put(48, new Snapshot(Material.ARROW, "world.editor.nav.prev"));
        baseline.put(49, new Snapshot(Material.NETHER_STAR, "world.editor.create.button-name"));
        baseline.put(50, new Snapshot(Material.ARROW, "world.editor.nav.next"));
        return baseline;
    }

    /** Render the engine menu for the player and snapshot its populated, non-filler slots. */
    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        Inventory inv = player.getOpenInventory().getTopInventory();
        return snapshot(inv);
    }

    /** Build the engine, register the worlds bindings + spec, and open the picker for the player. */
    private void openEngine() {
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());

        WorldListMenu listMenu = listMenu(menus, bindings);
        listMenu.register(bindings, dataFolder, NOOP);
        listMenu.open(player, viewer);
    }

    /** A {@link WorldListMenu} wired off the same collaborators as the old view, over the engine façade. */
    private WorldListMenu listMenu(Menus menus, MenuBindings bindings) {
        WorldMainMenu mainMenu = new WorldMainMenu(menus, scheduler, repository, engine, (p, v) -> {});
        Notifier notifier = new Notifier(new KeyMessages(), new SilentSink());
        CreateWorld createWorld =
                new CreateWorld(repository, engine, notifier, new SilentEvents(), scheduler, Clock.systemUTC());
        WorldCreateMenu createMenu =
                new WorldCreateMenu(menus, scheduler, createWorld, notifier, textInput, (p, v) -> {});
        // The list's left click opens the main hub, so its spec must be registered for the open to resolve; the grid
        // and generation sub-screens are exercised by their own golden tests, so they are not wired here.
        mainMenu.register(bindings, dataFolder, NOOP);
        return new WorldListMenu(menus, scheduler, repository, engine, worldTeleport, mainMenu, createMenu);
    }

    /** Click the given content slot of the open menu with the given gesture through the production click path. */
    private void fireClick(int slot, ClickType click) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, click, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The slot -> (material, plain name) map for every non-empty, non-filler slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.BLACK_STAINED_GLASS_PANE) {
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

    private void seed(String name, WorldEnvironment environment) {
        WorldSpec spec = new WorldSpec(
                environment,
                com.uxplima.uxmessentials.worlds.domain.WorldGenType.NORMAL,
                Optional.empty(),
                Optional.empty(),
                true,
                Optional.empty());
        repository.save(ManagedWorld.created(WorldName.of(name), spec, true, Optional.empty(), Instant.EPOCH));
    }

    private WorldTeleportService assembleTeleport() {
        Notifier notifier = new Notifier(new KeyMessages(), new SilentSink());
        WorldAccessPolicy policy = new WorldAccessPolicy(new UnlimitedPermissions(), engine);
        return new WorldTeleportService(
                repository,
                engine,
                policy,
                teleporter,
                new FreeEntryFee(),
                new UnlimitedPermissions(),
                new SilentEvents(),
                notifier,
                scheduler);
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    // --- fakes ---

    private static final class FakeWorldRepository implements WorldRepository {
        private final Map<String, ManagedWorld> byName = new LinkedHashMap<>();

        @Override
        public Optional<ManagedWorld> find(WorldName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<ManagedWorld> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(WorldName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(ManagedWorld world) {
            byName.put(world.name().value(), world);
        }

        @Override
        public void delete(WorldName name) {
            byName.remove(name.value());
        }
    }

    /** Every managed world is reported loaded with a fixed spawn point, so a forced teleport resolves a destination. */
    private static final class FakeWorldEngine implements WorldEngine {
        @Override
        public Result<Unit, WorldError> create(ManagedWorld world) {
            return Result.ok();
        }

        @Override
        public Result<Unit, WorldError> load(ManagedWorld world) {
            return Result.ok();
        }

        @Override
        public Result<Unit, WorldError> unload(WorldName name, boolean save) {
            return Result.ok();
        }

        @Override
        public Result<Unit, WorldError> deleteFiles(WorldName name) {
            return Result.ok();
        }

        @Override
        public Optional<DetectedWorld> scanFolder(WorldName name) {
            return Optional.empty();
        }

        @Override
        public boolean exists(WorldName name) {
            return true;
        }

        @Override
        public boolean isLoaded(WorldName name) {
            return true;
        }

        @Override
        public Set<WorldName> loadedWorldNames() {
            return Set.of();
        }

        @Override
        public Optional<WorldName> defaultWorldName() {
            return Optional.empty();
        }

        @Override
        public Optional<UUID> uidOf(WorldName name) {
            return Optional.empty();
        }

        @Override
        public int playerCount(WorldName name) {
            return 0;
        }

        @Override
        public Optional<Position> spawnPoint(WorldName name) {
            return Optional.of(SPAWN);
        }
    }

    private static final class RecordingTeleporter implements WorldTeleporter {
        private final List<PlayerRef> targets = new ArrayList<>();
        private final List<WorldTeleportCause> causes = new ArrayList<>();

        @Override
        public boolean teleport(PlayerRef who, Position to, WorldTeleportCause cause) {
            targets.add(who);
            causes.add(cause);
            return true;
        }
    }

    private static final class FreeEntryFee implements WorldEntryFee {
        @Override
        public boolean canAfford(PlayerRef who, java.math.BigDecimal amount) {
            return true;
        }

        @Override
        public boolean charge(PlayerRef who, java.math.BigDecimal amount) {
            return true;
        }
    }

    private static final class UnlimitedPermissions implements Permissions {
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

    private static final class SilentEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class SilentSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    /**
     * Surfaces the entry name's {@code world_name} token so a world's name appears; else the bare key. The engine
     * resolves a {@code @key} line through a lambda {@link MessageKey} carrying only the key string, so the match is
     * by {@link MessageKey#key()} rather than enum identity.
     */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(WorldEditorMessageKey.LIST_ENTRY_NAME.key())) {
                return placeholders.getOrDefault("world_name", "");
            }
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
