package com.uxplima.uxmessentials.playerstate.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.InvseeView;
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
 * MockBukkit coverage of the managed {@code /invsee} view: the menu mirrors the target's full inventory, main
 * slots, armour, and offhand. Into a private 54-slot copy; the viewer's edit to that copy is reconciled back
 * onto the target on close, and nothing is duplicated because the viewer never touches the target's live
 * {@code PlayerInventory} while the menu is open.
 *
 * <p>The scheduler is a synchronous double so the entity-bound open and the close write-back run inline, and the
 * close is dispatched as a real {@link InventoryCloseEvent} through the menu engine's own listener, as a live close
 * routes through. The conservation assertion is the dupe guard: the total item count across the target plus the
 * menu copy is the same before and after the edit, so an item moved inside the menu is moved, not cloned.
 */
class InvseeViewPathTest {

    private ServerMock server;
    private Plugin plugin;
    private MirrorWindow window;
    private InvseeView view;

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
        view = new InvseeView(new SyncScheduler(), window);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void mirrorsMainArmourAndOffhandIntoTheManagedMenu() {
        PlayerMock target = server.addPlayer("Target");
        target.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
        target.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        target.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        PlayerMock viewer = grantModify(server.addPlayer("Staff"));

        view.open(ref(viewer), ref(target));

        Inventory menu = viewer.getOpenInventory().getTopInventory();
        assertThat(menu.getSize()).isEqualTo(54);
        assertThat(menu.getItem(0)).isNotNull();
        assertThat(menu.getItem(0).getType()).isEqualTo(Material.DIAMOND);
        assertThat(menu.getItem(39).getType()).isEqualTo(Material.DIAMOND_HELMET); // helmet slot
        assertThat(menu.getItem(40).getType()).isEqualTo(Material.SHIELD); // offhand slot
        assertThat(menu.getItem(53).getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE); // filler
    }

    @Test
    void editInTheMenuIsWrittenBackOnCloseWithoutDuplicating() {
        PlayerMock target = server.addPlayer("Target");
        target.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
        PlayerMock viewer = grantModify(server.addPlayer("Staff"));
        int before = totalDiamonds(target);
        view.open(ref(viewer), ref(target));
        Inventory menu = viewer.getOpenInventory().getTopInventory();

        // The viewer relocates the stack inside the private menu copy from slot 0 to slot 7, then closes.
        ItemStack moved = menu.getItem(0);
        menu.setItem(0, null);
        menu.setItem(7, moved);
        server.getPluginManager().callEvent(new InventoryCloseEvent(viewer.getOpenInventory()));

        // The move landed on the target (slot 0 emptied, slot 7 holds the stack) and nothing was duplicated:
        // the close write-back overwrites the target's slots from the menu copy, it never adds to them.
        assertThat(target.getInventory().getItem(0)).isNull();
        assertThat(target.getInventory().getItem(7)).isNotNull();
        assertThat(target.getInventory().getItem(7).getType()).isEqualTo(Material.DIAMOND);
        assertThat(before).isEqualTo(5);
        assertThat(totalDiamonds(target)).isEqualTo(before); // conserved: 5 in, 5 out, no 2x
    }

    @Test
    void selfInvseeOpensAndWritesBackToYourself() {
        PlayerMock self = grantModify(server.addPlayer("Solo"));
        self.getInventory().setItem(2, new ItemStack(Material.EMERALD, 3));

        view.open(ref(self), ref(self));
        Inventory menu = self.getOpenInventory().getTopInventory();
        menu.setItem(2, new ItemStack(Material.EMERALD, 9)); // grow the stack inside the menu
        server.getPluginManager().callEvent(new InventoryCloseEvent(self.getOpenInventory()));

        assertThat(self.getInventory().getItem(2)).isNotNull();
        assertThat(self.getInventory().getItem(2).getAmount()).isEqualTo(9);
    }

    @Test
    void readsTheTargetsInventoryOnTheTargetsEntityThreadBeforeOpeningForTheViewer() {
        PlayerMock target = server.addPlayer("Target");
        target.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
        PlayerMock viewer = grantModify(server.addPlayer("Staff"));
        RecordingScheduler recording = new RecordingScheduler();
        InvseeView recordingView = new InvseeView(recording, window);

        recordingView.open(ref(viewer), ref(target));

        // The target's live inventory is read on the TARGET's entity thread first (Folia ownership), then the menu is
        // built and opened on the VIEWER's thread: the first hop is the target, and the viewer is hopped to as well.
        assertThat(recording.entityHops).hasSizeGreaterThanOrEqualTo(2);
        assertThat(recording.entityHops.get(0).uuid()).isEqualTo(target.getUniqueId());
        assertThat(recording.entityHops).extracting(PlayerRef::uuid).contains(viewer.getUniqueId());
        // And the read still produced the right snapshot: the menu mirrors the target's diamond.
        Inventory menu = viewer.getOpenInventory().getTopInventory();
        assertThat(menu.getItem(0)).isNotNull();
        assertThat(menu.getItem(0).getType()).isEqualTo(Material.DIAMOND);
    }

    @Test
    void withoutModifyTheMenuStillOpensAndNeverDuplicatesOnClose() {
        PlayerMock target = server.addPlayer("Target");
        target.getInventory().setItem(0, new ItemStack(Material.GOLD_INGOT, 4));
        PlayerMock viewer = server.addPlayer("Watcher"); // no modify node

        view.open(ref(viewer), ref(target));
        Inventory menu = viewer.getOpenInventory().getTopInventory();
        assertThat(menu.getSize()).isEqualTo(54);
        server.getPluginManager().callEvent(new InventoryCloseEvent(viewer.getOpenInventory()));

        assertThat(target.getInventory().getItem(0).getAmount()).isEqualTo(4);
    }

    @Test
    void withoutModifyEveryMovementInTheMirrorIsCancelledWhileTheFillerIsNeverMovableAtAll() {
        PlayerMock target = server.addPlayer("Target");
        target.getInventory().setItem(0, new ItemStack(Material.GOLD_INGOT, 4));
        PlayerMock watcher = server.addPlayer("Watcher"); // no modify node
        view.open(ref(watcher), ref(target));

        // A viewer without the modify node may look but not touch: putting a stack into the mirrored region is
        // refused, so nothing they do can reach the target through the close write-back.
        watcher.setItemOnCursor(new ItemStack(Material.DIAMOND));
        assertThat(click(watcher, 0).isCancelled()).isTrue();

        // The same click by a viewer who does hold the node goes through, so the refusal above is the missing
        // permission rather than the window cancelling everything.
        PlayerMock staff = grantModify(server.addPlayer("Staff"));
        view.open(ref(staff), ref(target));
        staff.setItemOnCursor(new ItemStack(Material.DIAMOND));
        assertThat(click(staff, 0).isCancelled()).isFalse();

        // The filler past the offhand slot maps to nothing a player carries, so it stays chrome for everyone: a
        // click that could carry one of those panes out of the window would be minting an item.
        assertThat(click(staff, 53).isCancelled()).isTrue();
    }

    /** Dispatch a left click with the viewer's cursor onto {@code slot} of their open window. */
    private InventoryClickEvent click(PlayerMock viewer, int slot) {
        InventoryClickEvent event = new InventoryClickEvent(
                viewer.getOpenInventory(),
                InventoryType.SlotType.CONTAINER,
                slot,
                ClickType.LEFT,
                InventoryAction.PLACE_ALL);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private PlayerMock grantModify(PlayerMock player) {
        player.addAttachment(plugin, "uxmessentials.invsee.modify", true);
        return player;
    }

    private static int totalDiamonds(PlayerMock player) {
        return countDiamonds(player.getInventory().getContents());
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
