package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffPlayerMenu;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import com.uxplima.uxmessentials.staff.application.port.StaffTeleport;
import com.uxplima.uxmlib.gui.Guis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The {@code /stafflist} golden test: the engine-rendered staff picker must draw the exact window the deleted
 * {@code StaffListView} drew. The fixture is three online staff members, so the window draws a PLAYER_HEAD named
 * with each member's name at content slots 0, 1, 2, with the rest of the grid and the whole bottom row empty, the
 * old picker placed no filler and no navigation buttons. The engine's window is snapshotted as
 * {@code (slot -> material, plain name)} and asserted equal, slot for slot, to the baseline the old view produced,
 * captured once while both rendered the same fixture and frozen here as the contract so the old class could be
 * deleted. A second case clicks a head through the engine's own {@link MenuListener} and proves the migrated path
 * admin-teleports the looker onto that staff member through the same {@link StaffTeleport} the old picker's click
 * drove. The empty-roster branch lives in {@code StaffListCommand}, covered by its own test.
 */
class StaffListGoldenTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock looker;
    private PlayerRef viewer;
    private RecordingTeleport teleport;
    private RecordingKeySink sink;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        looker = server.addPlayer("Looker");
        viewer = new PlayerRef(looker.getUniqueId(), looker.getName());
        teleport = new RecordingTeleport();
        sink = new RecordingKeySink();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameStaffHeadGridAndEmptyBottomRowAsTheOldView() {
        StaffPlayerMenu menu = engine();
        menu.openList(viewer, roster());

        Inventory inv = looker.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(54);
        assertThat(snapshot(inv)).isEqualTo(baseline());
    }

    @Test
    void clickingAStaffHeadThroughTheEngineTeleportsToThatMember() {
        List<PlayerRef> roster = roster();
        StaffPlayerMenu menu = engine();
        menu.openList(viewer, roster);

        fireClick(2); // content slot 2 is the third staff member

        assertThat(teleport.targets)
                .extracting(PlayerRef::uuid)
                .containsExactly(roster.get(2).uuid());
        assertThat(sink.keys).contains(StaffMessageKey.STAFF_TELEPORTED);
    }

    /** Three online staff members, so the click can re-resolve the clicked head to a live target. */
    private List<PlayerRef> roster() {
        List<PlayerRef> out = new ArrayList<>();
        for (String name : List.of("Mod", "Admin", "Helper")) {
            PlayerMock member = server.addPlayer(name);
            out.add(new PlayerRef(member.getUniqueId(), member.getName()));
        }
        return out;
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code StaffListView} produced for this fixture (three
     * staff), captured once while both paths rendered it identically and frozen here as the contract: a PLAYER_HEAD
     * named with each member's name at content slots 0, 1, 2, and nothing else. The old picker placed no filler and
     * no navigation buttons, so the rest of the grid and the whole bottom row are empty.
     */
    private static Map<Integer, Snapshot> baseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.PLAYER_HEAD, "Mod"));
        baseline.put(1, new Snapshot(Material.PLAYER_HEAD, "Admin"));
        baseline.put(2, new Snapshot(Material.PLAYER_HEAD, "Helper"));
        return baseline;
    }

    /** Wire the engine over the same collaborators the old view used and register the picker's bindings + specs. */
    private StaffPlayerMenu engine() {
        GuiText guiText = new GuiText(new KeyMessages());
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        StaffPlayerMenu menu = new StaffPlayerMenu(menus, server, new KeyMessages(), sink, teleport);
        menu.register(bindings, specDir(), new NoopLogger());
        return menu;
    }

    /** The bundled spec directory under the source tree, so the test loads the shipped navigator/list specs. */
    private static Path specDir() {
        Path repoRoot = Path.of("").toAbsolutePath();
        while (repoRoot != null && !java.nio.file.Files.exists(repoRoot.resolve("settings.gradle.kts"))) {
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

    /** Records the teleport targets the click drove, so the test asserts the looker was sent to the clicked head. */
    private static final class RecordingTeleport implements StaffTeleport {
        private final List<PlayerRef> targets = new ArrayList<>();

        @Override
        public boolean teleportTo(PlayerRef staff, PlayerRef target) {
            targets.add(target);
            return true;
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
