package com.uxplima.uxmessentials.playerstate.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.EnderseeView;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.MirrorWindow;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the managed online {@code /endersee} view: the menu mirrors the target's 27-slot ender
 * chest into a private copy; the viewer's edit to that copy is reconciled back onto the target on close, and
 * nothing is duplicated because the viewer never touches the target's live ender-chest container while the menu is
 * open. This is the ender-chest counterpart of {@link InvseeViewPathTest}, and the dispatch assertion proves the
 * Folia ownership fix: the target's contents are snapshotted on the target's own entity thread, the viewer is
 * hopped to for the GUI, and the close write-back lands on the subject's thread.
 *
 * <p>The scheduler is a synchronous double so the entity-bound open and the close write-back run inline, and the
 * close is dispatched as a real {@link InventoryCloseEvent} through the menu engine's own listener, the way a live
 * close routes. The conservation assertion is the dupe guard: the total item count across the target plus the
 * menu copy is the same before and after the edit, so an item moved inside the menu is moved, not cloned.
 */
class EnderseeViewPathTest {

    private ServerMock server;
    private Plugin plugin;
    private MirrorWindow window;
    private EnderseeView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        window = new MirrorWindow(
                new KeyMessages(),
                engine.menus(),
                new SyncScheduler(),
                java.nio.file.Path.of("no-such-data-folder"),
                TestMenuEngine.SILENT_LOG);
        window.register(engine.bindings());
        engine.installListener(plugin);
        view = new EnderseeView(new SyncScheduler(), window);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void mirrorsTheTargetsEnderChestIntoTheManagedMenu() {
        PlayerMock target = server.addPlayer("Target");
        target.getEnderChest().setItem(0, new ItemStack(Material.DIAMOND, 5));
        target.getEnderChest().setItem(26, new ItemStack(Material.EMERALD, 2));
        PlayerMock viewer = server.addPlayer("Staff");

        view.open(ref(viewer), ref(target));

        Inventory menu = viewer.getOpenInventory().getTopInventory();
        assertThat(menu.getSize()).isEqualTo(27);
        assertThat(menu.getItem(0)).isNotNull();
        assertThat(menu.getItem(0).getType()).isEqualTo(Material.DIAMOND);
        assertThat(menu.getItem(26).getType()).isEqualTo(Material.EMERALD);
    }

    @Test
    void editInTheMenuIsWrittenBackOnCloseWithoutDuplicating() {
        PlayerMock target = server.addPlayer("Target");
        target.getEnderChest().setItem(0, new ItemStack(Material.DIAMOND, 5));
        PlayerMock viewer = server.addPlayer("Staff");
        int before = totalDiamonds(target);
        view.open(ref(viewer), ref(target));
        Inventory menu = viewer.getOpenInventory().getTopInventory();

        // The viewer relocates the stack inside the private menu copy from slot 0 to slot 7, then closes.
        ItemStack moved = menu.getItem(0);
        menu.setItem(0, null);
        menu.setItem(7, moved);
        server.getPluginManager().callEvent(new InventoryCloseEvent(viewer.getOpenInventory()));

        // The move landed on the target's ender chest (slot 0 emptied, slot 7 holds the stack) and nothing was
        // duplicated: the close write-back overwrites the target's slots from the menu copy, it never adds to them.
        assertThat(target.getEnderChest().getItem(0)).isNull();
        assertThat(target.getEnderChest().getItem(7)).isNotNull();
        assertThat(target.getEnderChest().getItem(7).getType()).isEqualTo(Material.DIAMOND);
        assertThat(before).isEqualTo(5);
        assertThat(totalDiamonds(target)).isEqualTo(before); // conserved: 5 in, 5 out, no 2x
    }

    @Test
    void snapshotsOnTheTargetThreadOpensOnTheViewerThreadAndWritesBackOnTheSubjectThread() {
        PlayerMock target = server.addPlayer("Target");
        target.getEnderChest().setItem(0, new ItemStack(Material.DIAMOND, 5));
        PlayerMock viewer = server.addPlayer("Staff");
        RecordingScheduler recording = new RecordingScheduler();
        // The window each view opens carries its own write-back, so the engine's single listener routes this close
        // back to the recording view without a second listener registration.
        EnderseeView recordingView = new EnderseeView(recording, window);

        recordingView.open(ref(viewer), ref(target));

        // The target's live ender chest is read on the TARGET's entity thread first (Folia ownership), then the menu
        // is built and opened on the VIEWER's thread: the first hop is the target, and the viewer is hopped to too.
        assertThat(recording.entityHops).hasSizeGreaterThanOrEqualTo(2);
        assertThat(recording.entityHops.get(0).uuid()).isEqualTo(target.getUniqueId());
        assertThat(recording.entityHops).extracting(PlayerRef::uuid).contains(viewer.getUniqueId());
        // And the read still produced the right snapshot: the menu mirrors the target's diamond.
        Inventory menu = viewer.getOpenInventory().getTopInventory();
        assertThat(menu.getItem(0)).isNotNull();
        assertThat(menu.getItem(0).getType()).isEqualTo(Material.DIAMOND);

        // The close write-back hops to the SUBJECT's entity thread before mutating their ender chest.
        recording.entityHops.clear();
        ItemStack moved = menu.getItem(0);
        menu.setItem(0, null);
        menu.setItem(7, moved);
        server.getPluginManager().callEvent(new InventoryCloseEvent(viewer.getOpenInventory()));
        assertThat(recording.entityHops).extracting(PlayerRef::uuid).containsExactly(target.getUniqueId());
        assertThat(target.getEnderChest().getItem(7)).isNotNull();
        assertThat(target.getEnderChest().getItem(7).getType()).isEqualTo(Material.DIAMOND);
    }

    private static int totalDiamonds(PlayerMock player) {
        return countDiamonds(player.getEnderChest().getContents());
    }

    private static int countDiamonds(ItemStack[] contents) {
        return Arrays.stream(contents)
                .filter(stack -> stack != null && stack.getType() == Material.DIAMOND)
                .mapToInt(ItemStack::getAmount)
                .sum();
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    /** Resolves a title key to its plain key string; MiniMessage parses it as literal text in the view. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Runs every scheduled task inline so the entity-bound open and the close write-back complete in-test. */
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

    /** Runs every task inline (so the open completes in-test) but records each {@code onEntity} hop in order. */
    private static final class RecordingScheduler implements Scheduler {
        private final List<PlayerRef> entityHops = new ArrayList<>();

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
            entityHops.add(player);
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
