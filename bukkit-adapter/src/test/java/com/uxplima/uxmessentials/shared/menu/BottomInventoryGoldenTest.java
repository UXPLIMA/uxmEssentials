package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuItemMark;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The end-to-end proof for the bottom-inventory 90-slot menu, driven config → open → click → close/death through the
 * real {@link Menus} engine and the live {@code MenuListener}. A bottom-inventory menu paints an item into the
 * viewer's own inventory slots (raw slot 54 → player slot 9), so opening one must snapshot and clear those real slots,
 * a click on a bottom slot must route to that item's actions with no listener change, closing must restore the real
 * inventory, and dying with one open must drop the real items rather than the menu tiles. A menu that leaves the flag
 * off never touches the player inventory at all.
 *
 * <p>The scheduler used here queues every entity hop so the deferral is exercised for real: an open, a click and a
 * close each enqueue their work, and {@link QueueingScheduler#drain()} runs it, so the close-restore is asserted to
 * be a scheduled next-tick pass, not an inline mutation inside {@link InventoryCloseEvent} (the Paper gotcha the
 * production code defers around).
 *
 * <p>MockBukkit models the {@code PlayerInventory} storage contents and per-item persistent-data mark this leans on,
 * so the snapshot/clear/paint, the mapped raw-slot routing and the death-drop rewrite all run against real state. The
 * click and death paths drive the raw slot and the drop list directly (an {@code InventoryClickEvent} carrying raw
 * slot 54, a {@code PlayerDeathEvent} carrying the drop list), because MockBukkit does not model a chest
 * {@code InventoryView}'s raw-slot geometry or a server-computed death drop set. The engine's own mapping and guard
 * are what these assert.
 */
class BottomInventoryGoldenTest {

    private static final String BOTTOM_HOCON = """
            bottom-inventory = true
            rows = 6
            items {
              top { slot = 4, material = DIAMOND, name = "top" }
              bot { slot = 54, material = EMERALD, name = "bottom", click { left = ["record:clicked"] } }
            }
            """;

    private static final String PLAIN_HOCON = """
            rows = 1
            items { x { slot = 0, material = STONE, name = "x" } }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private Menus menus;
    private QueueingScheduler scheduler;
    private List<String> notes;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        notes = new ArrayList<>();
        scheduler = new QueueingScheduler();

        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.bindings().action("record", ctx -> notes.add(ctx.arg()));
        menus = engine.menus();
        menus.registerSpec("bottom", new MenuSpecLoader().parse(BOTTOM_HOCON));
        menus.registerSpec("plain", new MenuSpecLoader().parse(PLAIN_HOCON));
        engine.installListener(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void openingSnapshotsAndClearsThePlayerInventoryThenPaintsTheBottomItem() {
        player.getInventory().setItem(9, new ItemStack(Material.DIAMOND));
        player.getInventory().setItem(20, new ItemStack(Material.GOLD_INGOT));

        openBottom();

        assertThat(player.getInventory().getItem(9))
                .as("the bottom item paints into player slot 9, the raw-slot-54 mapping")
                .isNotNull()
                .extracting(ItemStack::getType)
                .isEqualTo(Material.EMERALD);
        assertThat(MenuItemMark.isMarked(player.getInventory().getItem(9)))
                .as("the painted bottom tile is an engine-rendered item, so it carries the anti-dupe mark")
                .isTrue();
        assertThat(player.getInventory().getItem(20))
                .as("the rest of the real inventory is cleared to make the 90-slot canvas")
                .isNull();
        assertThat(player.getOpenInventory().getTopInventory().getItem(4))
                .as("the top item still renders into the chest exactly as an ordinary menu does")
                .isNotNull()
                .extracting(ItemStack::getType)
                .isEqualTo(Material.DIAMOND);
    }

    @Test
    void aClickOnABottomRawSlotFiresThatItemsActions() {
        openBottom();

        clickRaw(54);
        scheduler.drain();

        assertThat(notes)
                .as("a click on raw slot 54 routes through the holder's click map to the bottom item's actions")
                .containsExactly("clicked");
    }

    @Test
    void closingSchedulesTheDeferredRestoreThatPutsTheRealInventoryBack() {
        player.getInventory().setItem(9, new ItemStack(Material.DIAMOND));
        openBottom();

        server.getPluginManager().callEvent(new InventoryCloseEvent(player.getOpenInventory()));
        assertThat(player.getInventory().getItem(9))
                .as("the restore is deferred, so before the scheduled task runs the menu tile is still in place")
                .extracting(ItemStack::getType)
                .isEqualTo(Material.EMERALD);

        scheduler.drain();

        assertThat(player.getInventory().getItem(9))
                .as("running the deferred task restores the real diamond to its slot")
                .isEqualTo(new ItemStack(Material.DIAMOND));
        assertThat(MenuItemMark.isMarked(player.getInventory().getItem(9)))
                .as("the menu tile is gone. The restore overwrote the whole canvas with the real snapshot")
                .isFalse();
    }

    @Test
    void dyingWithABottomMenuOpenDropsTheRealItemsAndNotTheMenuTiles() {
        player.getInventory().setItem(9, new ItemStack(Material.DIAMOND));
        openBottom();

        ItemStack menuTile = player.getInventory().getItem(9);
        List<ItemStack> drops = new ArrayList<>();
        drops.add(menuTile);
        PlayerDeathEvent death = new PlayerDeathEvent(
                player, DamageSource.builder(DamageType.GENERIC).build(), drops, 0, Component.empty(), false);

        server.getPluginManager().callEvent(death);

        assertThat(death.getDrops())
                .as("the marked menu tile is stripped from the drops so it cannot drop and dupe")
                .noneMatch(MenuItemMark::isMarked);
        assertThat(death.getDrops())
                .as("the viewer's real snapshotted diamond drops in its place, as an ordinary death would")
                .contains(new ItemStack(Material.DIAMOND));
    }

    @Test
    void aNonBottomMenuNeverTouchesThePlayerInventory() {
        player.getInventory().setItem(9, new ItemStack(Material.DIAMOND));

        menus.open(ref(), "plain", null);
        scheduler.drain();

        assertThat(player.getInventory().getItem(9))
                .as("a menu without the flag is byte-identical: the player inventory is left exactly as it was")
                .isEqualTo(new ItemStack(Material.DIAMOND));
    }

    private void openBottom() {
        menus.open(ref(), "bottom", null);
        scheduler.drain();
    }

    private PlayerRef ref() {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private void clickRaw(int rawSlot) {
        InventoryView view = player.getOpenInventory();
        server.getPluginManager()
                .callEvent(new InventoryClickEvent(
                        view, InventoryType.SlotType.CONTAINER, rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /**
     * A scheduler that runs global/region/async work inline but queues every per-entity hop, so a test can drive the
     * open, click and close deferrals by hand. Proving the close-restore is a scheduled next-tick pass rather than an
     * inline mutation inside {@link InventoryCloseEvent}.
     */
    private static final class QueueingScheduler implements Scheduler {
        private final Deque<Runnable> queued = new ArrayDeque<>();

        void drain() {
            while (!queued.isEmpty()) {
                queued.poll().run();
            }
        }

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
            queued.add(task);
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}
