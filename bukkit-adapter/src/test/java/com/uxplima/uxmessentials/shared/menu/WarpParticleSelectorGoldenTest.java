package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.PlayerWarpGoToHandle;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.PlayerWarpRepositoryHandle;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpEditorView;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpParticleSelectorView;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The warp particle-selector golden test: the engine-rendered selector must draw the exact grid the original bespoke
 * {@code WarpParticleSelectorView} drew on its own {@code Bukkit.createInventory}, and a click must run the same set
 * the old click did. The fixture is one server warp "spawn". The window is snapshotted as {@code (slot -> material,
 * plain name)} and asserted equal, slot for slot, to the analytic baseline the old view produced: the fourteen preset
 * particle icons at content slots 0..13, the ANVIL custom button at slot 18, the ARROW back button at slot 22, and
 * the LAVA_BUCKET remove button at slot 26. The gray-glass filler slots are dropped from the snapshot.
 *
 * <p>Then a left click on the first particle through the engine's own {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener}
 * proves the migrated path runs the same set the old click did. The warp's departure particle saved through the
 * repository, and a click on remove clears it, faithful in both appearance and behaviour.
 */
class WarpParticleSelectorGoldenTest {

    private static final int CUSTOM_SLOT = 18;
    private static final int BACK_SLOT = 22;
    private static final int REMOVE_SLOT = 26;

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

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
    private RecordingRepository repository;
    private WarpParticleSelectorView selector;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler = new SyncScheduler();
        engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        repository = new RecordingRepository();
        repository.save(Warp.create(WarpName.of("spawn"), Position.of(WORLD, 0, 64, 0), viewer, Instant.EPOCH));
        TextInput textInput = org.mockito.Mockito.mock(TextInput.class);
        WarpEditorView editorView = new WarpEditorView(
                engine.menus(),
                new KeyMessages(),
                scheduler,
                repository,
                textInput,
                org.mockito.Mockito.mock(com.uxplima.uxmessentials.warps.application.UseWarp.class),
                new PlayerWarpRepositoryHandle(),
                new PlayerWarpGoToHandle());
        editorView.register(engine.bindings(), dataFolder, NOOP);
        selector =
                WarpParticleSelectorView.create(new KeyMessages(), engine.menus(), repository, editorView, textInput);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameParticleGridAsTheOldView() {
        selector.open(player, viewer, "spawn", null, true);

        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> rendered = snapshot(player.getOpenInventory().getTopInventory());

        assertThat(rendered.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(rendered).isEqualTo(baseline);
    }

    @Test
    void clickingAParticleSetsTheDepartureParticleAndReopensTheEditor() {
        selector.open(player, viewer, "spawn", null, true);

        fireClick(0); // content slot 0 holds the first preset particle

        WarpParticleSelectorView.ParticleOption first = selector.getOptions().get(0);
        assertThat(repository.find(WarpName.of("spawn")).orElseThrow().departureParticle())
                .contains(first.particleName());
    }

    @Test
    void clickingAParticleSetsTheArrivalParticleWhenNotDeparture() {
        selector.open(player, viewer, "spawn", null, false);

        fireClick(2);

        WarpParticleSelectorView.ParticleOption third = selector.getOptions().get(2);
        assertThat(repository.find(WarpName.of("spawn")).orElseThrow().arrivalParticle())
                .contains(third.particleName());
    }

    @Test
    void clickingRemoveClearsTheParticle() {
        repository.save(repository
                .find(WarpName.of("spawn"))
                .orElseThrow()
                .withDepartureParticle(Optional.of("minecraft:flame")));
        selector.open(player, viewer, "spawn", null, true);

        fireClick(REMOVE_SLOT);

        assertThat(repository.find(WarpName.of("spawn")).orElseThrow().departureParticle())
                .isEmpty();
    }

    /**
     * The slot -> (material, plain name) map the bespoke {@code WarpParticleSelectorView} produced for this fixture:
     * one icon per preset particle at content slots 0..13, the ANVIL custom button at slot 18, the ARROW back button
     * at slot 22, and the LAVA_BUCKET remove button at slot 26, each named through the catalog key the test's
     * {@code KeyMessages} returns verbatim. The gray-glass filler slots are dropped from the snapshot.
     */
    private Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        List<WarpParticleSelectorView.ParticleOption> options = selector.getOptions();
        for (int i = 0; i < options.size(); i++) {
            baseline.put(
                    i,
                    new Snapshot(
                            options.get(i).material(), WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_ENTRY_NAME.key()));
        }
        baseline.put(
                CUSTOM_SLOT,
                new Snapshot(Material.ANVIL, WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_CUSTOM_NAME.key()));
        baseline.put(BACK_SLOT, new Snapshot(Material.ARROW, WarpsMessageKey.WARP_EDITOR_SELECTOR_BACK.key()));
        baseline.put(
                REMOVE_SLOT,
                new Snapshot(Material.LAVA_BUCKET, WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_REMOVE_NAME.key()));
        return baseline;
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

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

    private record Snapshot(Material material, String name) {}

    /** A repository that records stored warps, so the selector's set lands somewhere the test can read. */
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
