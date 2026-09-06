package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffExamineMenu;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffPlayerMenu;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import com.uxplima.uxmessentials.staff.application.port.StaffInspector;
import com.uxplima.uxmessentials.staff.application.port.StaffTeleport;
import com.uxplima.uxmlib.gui.Guis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The EXAMINE gadget golden test: the engine-rendered examine picker must draw the exact window the deleted
 * {@code StaffExamineView} drew. The fixture is three online players, so the window draws a PLAYER_HEAD named with
 * each player's name at content slots 0, 1, 2, with the rest of the grid and the whole bottom row empty, the old
 * picker placed no filler and no navigation buttons. The engine's window is snapshotted as
 * {@code (slot -> material, plain name)} and asserted equal, slot for slot, to the baseline the old view produced,
 * captured once while both rendered the same fixture and frozen here as the contract so the old class could be
 * deleted. A second case clicks a head through the engine's own {@link MenuListener} and proves the migrated path
 * inspects that player through the same {@link StaffInspector} the old view's click drove and delivers the same
 * {@code STAFF_EXAMINE_INFO} line.
 */
class StaffExamineGoldenTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock looker;
    private PlayerRef viewer;
    private RecordingInspector inspector;
    private RecordingKeySink sink;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        looker = server.addPlayer("Looker");
        viewer = new PlayerRef(looker.getUniqueId(), looker.getName());
        inspector = new RecordingInspector();
        sink = new RecordingKeySink();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameHeadGridAndEmptyBottomRowAsTheOldView() {
        StaffExamineMenu menu = engine();
        menu.open(viewer, roster());

        Inventory inv = looker.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(54);
        assertThat(snapshot(inv)).isEqualTo(baseline());
    }

    @Test
    void clickingAHeadThroughTheEngineExaminesThatPlayer() {
        List<PlayerRef> roster = roster();
        StaffExamineMenu menu = engine();
        menu.open(viewer, roster);

        fireClick(0); // content slot 0 is the first candidate

        assertThat(inspector.inspected)
                .extracting(PlayerRef::uuid)
                .containsExactly(roster.get(0).uuid());
        assertThat(sink.keys).contains(StaffMessageKey.STAFF_EXAMINE_INFO);
    }

    /** Three online players, so the click can re-resolve the clicked head to a live target. */
    private List<PlayerRef> roster() {
        List<PlayerRef> out = new ArrayList<>();
        for (String name : List.of("Una", "Vera", "Wren")) {
            PlayerMock candidate = server.addPlayer(name);
            out.add(new PlayerRef(candidate.getUniqueId(), candidate.getName()));
        }
        return out;
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code StaffExamineView} produced for this fixture (three
     * online players), captured once while both paths rendered it identically and frozen here as the contract: a
     * PLAYER_HEAD named with each player's name at content slots 0, 1, 2, and nothing else. The old picker placed no
     * filler and no navigation buttons, so the rest of the grid and the whole bottom row are empty.
     */
    private static Map<Integer, Snapshot> baseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.PLAYER_HEAD, "Una"));
        baseline.put(1, new Snapshot(Material.PLAYER_HEAD, "Vera"));
        baseline.put(2, new Snapshot(Material.PLAYER_HEAD, "Wren"));
        return baseline;
    }

    /** Wire the engine, register the shared player-head bindings and the examine click + spec. */
    private StaffExamineMenu engine() {
        GuiText guiText = new GuiText(new KeyMessages());
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        // The examine picker reuses the staff:players source + staff_player_name label StaffPlayerMenu registers, so
        // that one must register first; the examine menu then adds only its spec and the staff:examine click.
        new StaffPlayerMenu(menus, server, new KeyMessages(), new NoopSink(), new NoopTeleport())
                .register(bindings, specDir(), new NoopLogger());
        StaffExamineMenu menu = new StaffExamineMenu(menus, server, new KeyMessages(), sink, inspector);
        menu.register(bindings, specDir(), new NoopLogger());
        return menu;
    }

    /** The bundled spec directory under the source tree, so the test loads the shipped examine + player specs. */
    private static Path specDir() {
        Path repoRoot = Path.of("").toAbsolutePath();
        while (repoRoot != null && !Files.exists(repoRoot.resolve("settings.gradle.kts"))) {
            repoRoot = repoRoot.getParent();
        }
        Objects.requireNonNull(repoRoot, "repo root");
        return repoRoot.resolve("bukkit-adapter/src/main/resources");
    }

    private void fireClick(int slot) {
        InventoryView view = looker.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The slot -> (material, plain name) map for every non-empty slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null) {
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

    /** Records who was inspected, so the test asserts the clicked head's player was examined. */
    private static final class RecordingInspector implements StaffInspector {
        private final List<PlayerRef> inspected = new ArrayList<>();

        @Override
        public void inspect(PlayerRef looker, PlayerRef target) {
            inspected.add(target);
        }
    }

    /** Records which staff keys were delivered, by matching the bare key the test's {@code KeyMessages} returns. */
    private static final class RecordingKeySink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            for (StaffMessageKey key : StaffMessageKey.values()) {
                if (renderedText.startsWith(key.key())) {
                    keys.add(key);
                }
            }
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class NoopTeleport implements StaffTeleport {
        @Override
        public boolean teleportTo(PlayerRef staff, PlayerRef target) {
            return false;
        }
    }

    /** Resolves a key to its bare key string, ignoring placeholders, so a wrong key still mismatches. */
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

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
