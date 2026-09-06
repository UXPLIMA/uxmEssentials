package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

import com.uxplima.uxmessentials.messaging.adapter.inbound.gui.IgnoreListMenu;
import com.uxplima.uxmessentials.messaging.application.Ignore;
import com.uxplima.uxmessentials.messaging.application.Unignore;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.domain.IgnoreEntry;
import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The ignore-list golden test: the engine-rendered {@code /ignore} manager must draw the exact grid the original
 * {@code IgnoreListView} drew. A player ignores two others, so the engine draws two PLAYER_HEAD icons (content slots
 * 0 and 1), the add LIME_DYE button (slot 49), and the two nav ARROWs (slots 48 and 50). The engine's window is
 * snapshotted as {@code (slot -> material, plain name)} and asserted equal, slot for slot, to the baseline the old
 * view produced. Captured once while both rendered the same fixture, then frozen here as the contract so the old
 * class could be deleted (the glass filler is skipped, as in the pilot and vault golden tests). Then a click on the
 * first head through the engine's own {@link MenuListener} proves the migrated path un-ignores that player through
 * the same {@link Unignore} use case the {@code /unignore} command drives, and the add seam ignores a resolvable
 * name through {@link Ignore}, so the move is faithful in both appearance and behaviour.
 */
class IgnoreMenuGoldenTest {

    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;

    @TempDir
    Path dataFolder;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeIgnores ignores;
    private Ignore ignore;
    private Unignore unignore;
    private TextInput textInput;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        ignores = new FakeIgnores();
        Notifier notifier = new Notifier(new KeyMessages(), new NoopSink());
        ignore = new Ignore(ignores, notifier);
        unignore = new Unignore(ignores, notifier);
        textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameHeadsAddAndNavAsTheOldView() {
        ignores.ignore(viewer, ref("Mallory"), IgnoreScope.ALL);
        ignores.ignore(viewer, ref("Eve"), IgnoreScope.ALL);

        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void clickingAHeadThroughTheEngineUnignoresThroughTheUseCase() {
        ignores.ignore(viewer, ref("Mallory"), IgnoreScope.ALL);
        openEngine();
        assertThat(ignores.load(viewer).ignores(ref("Mallory"))).isTrue();

        fireClick(0, ClickType.LEFT); // content slot 0 is the first ignored head

        assertThat(ignores.load(viewer).ignores(ref("Mallory"))).isFalse();
    }

    @Test
    void addingAResolvableNameIgnoresThroughTheUseCase() {
        IgnoreListMenu menu = openEngine();
        assertThat(ignores.load(viewer).size()).isZero();

        menu.addByName(viewer, "Mallory"); // the seam the add prompt routes a submitted line to

        assertThat(ignores.load(viewer).ignores(ref("Mallory"))).isTrue();
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code IgnoreListView} produced for this fixture (two
     * ignores), captured once while both paths rendered it identically and frozen here as the contract: two
     * PLAYER_HEAD heads at content slots 0 and 1, the add LIME_DYE button at slot 49, and the two nav ARROWs at
     * slots 48 and 50. The plain names are the bare catalog keys because the test's {@code KeyMessages} returns each
     * key verbatim, so a wrong key or material still mismatches.
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.PLAYER_HEAD, "messaging.gui.ignore.entry-name"));
        baseline.put(1, new Snapshot(Material.PLAYER_HEAD, "messaging.gui.ignore.entry-name"));
        baseline.put(48, new Snapshot(Material.ARROW, "messaging.gui.ignore.prev"));
        baseline.put(49, new Snapshot(Material.LIME_DYE, "messaging.gui.ignore.add"));
        baseline.put(50, new Snapshot(Material.ARROW, "messaging.gui.ignore.next"));
        return baseline;
    }

    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    /** Build the engine, register the ignore bindings + spec, and open the manager for the player. */
    private IgnoreListMenu openEngine() {
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());

        IgnoreListMenu menu =
                new IgnoreListMenu(menus, scheduler, ignores, ignore, unignore, new OnlineLookup(), textInput);
        menu.register(bindings, dataFolder, NOOP);
        menu.open(viewer);
        return menu;
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The slot -> (material, plain name) map for every populated, non-filler slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == FILLER) {
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

    private static PlayerRef ref(String name) {
        return new PlayerRef(uuid(name), name);
    }

    private static UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    /** An in-memory ignore store the test seeds; the same shape the production GUI test fake uses. */
    private static final class FakeIgnores implements IgnoreStore {
        private final Map<UUID, Map<UUID, PlayerRef>> store = new HashMap<>();

        @Override
        public IgnoreList load(PlayerRef owner) {
            Map<UUID, PlayerRef> entries = store.getOrDefault(owner.uuid(), Map.of());
            List<IgnoreEntry> rows = new ArrayList<>();
            for (PlayerRef ignored : entries.values()) {
                rows.add(IgnoreEntry.all(ignored));
            }
            return IgnoreList.of(owner, rows);
        }

        @Override
        public void ignore(PlayerRef owner, PlayerRef ignored, IgnoreScope scope) {
            store.computeIfAbsent(owner.uuid(), k -> new LinkedHashMap<>()).put(ignored.uuid(), ignored);
        }

        @Override
        public void unignore(PlayerRef owner, PlayerRef ignored) {
            Map<UUID, PlayerRef> entries = store.get(owner.uuid());
            if (entries != null) {
                entries.remove(ignored.uuid());
            }
        }
    }

    private static final class OnlineLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.of(ref(name));
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.of(new PlayerRef(uuid, "player"));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return true;
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
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
