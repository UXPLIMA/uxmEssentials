package com.uxplima.uxmessentials.itemworld.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.itemworld.application.ItemworldConfig;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
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
 * MockBukkit coverage of the in-inventory shulker view. Right-clicking a shulker box in hand opens its contents as
 * a 27-slot editable view; edits are written back into the box item on close (round-trip); the source box is locked
 * against being moved while the view is open (no dupe); a player who logs out with the view still open has their
 * edits written back as they leave, because an abandoned view would duplicate whatever they had dragged out of the
 * box; and a disabled sub-feature, a missing permission, or a non-shulker item each leave vanilla behaviour
 * untouched. The interact is driven straight through the listener (a
 * unit test of the handler), while clicks and the close go through MockBukkit's own dispatch.
 */
class ShulkerBoxViewTest {

    private static final String PERMISSION = "uxmessentials.itemworld.shulker";
    private static final int RAW_SOURCE_SLOT = 27 + 27; // top(27) + storage(27) + held hotbar slot 0

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private MutableConfig config;
    private ShulkerBoxView view;
    private ShulkerBoxListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        config = new MutableConfig();
        config.put("shulkers.require-sneak", false); // trigger on any right-click so tests need not simulate sneak
        view = new ShulkerBoxView(new KeyMessages(), new SyncScheduler());
        listener = new ShulkerBoxListener(view, ItemworldConfig.from(config));
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rightClickingAShulkerOpensAViewShowingItsContents() {
        grantPermission();
        holdShulkerContaining(0, new ItemStack(Material.DIAMOND, 3));

        rightClickHeldItem();

        Inventory top = player.getOpenInventory().getTopInventory();
        assertThat(top).isNotNull();
        assertThat(top.getSize()).isEqualTo(27);
        assertThat(top.getHolder()).isInstanceOf(ShulkerBoxHolder.class);
        assertThat(top.getItem(0)).isNotNull();
        assertThat(top.getItem(0).getType()).isEqualTo(Material.DIAMOND);
        assertThat(view.openCount()).isEqualTo(1);
    }

    @Test
    void editsAreWrittenBackIntoTheBoxOnCloseWithoutLoss() {
        grantPermission();
        holdShulkerContaining(0, new ItemStack(Material.DIAMOND, 3));
        rightClickHeldItem();
        Inventory top = player.getOpenInventory().getTopInventory();

        top.setItem(1, new ItemStack(Material.EMERALD, 2));
        server.getPluginManager().callEvent(new InventoryCloseEvent(player.getOpenInventory()));

        ItemStack[] contents = boxContents(player.getInventory().getItemInMainHand());
        assertThat(contents[0]).isNotNull();
        assertThat(contents[0].getType()).isEqualTo(Material.DIAMOND); // original untouched, no loss
        assertThat(contents[1]).isNotNull();
        assertThat(contents[1].getType()).isEqualTo(Material.EMERALD); // edit written back
        assertThat(contents[1].getAmount()).isEqualTo(2);
        assertThat(view.openCount()).isZero();
    }

    @Test
    void quittingWithTheViewOpenStillWritesTheEditsBack() {
        grantPermission();
        holdShulkerContaining(0, new ItemStack(Material.DIAMOND, 3));
        rightClickHeldItem();
        Inventory top = player.getOpenInventory().getTopInventory();

        // The player takes the diamonds out of the box and logs out without closing the window. Their inventory
        // keeps the diamonds, so the box must not keep a copy of them as well.
        top.setItem(0, null);
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 3));
        listener.onQuit(new PlayerQuitEvent(player, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertThat(boxContents(player.getInventory().getItem(0))[0]).isNull();
        assertThat(view.openCount()).isZero();
    }

    @Test
    void movingTheSourceBoxWhileOpenIsPrevented() {
        // The dupe guard: a click on the locked source hotbar slot is refused (MockBukkit cannot dispatch a real
        // InventoryClickEvent, its simulateInventoryClick is a no-op, so the lock decision is asserted directly).
        InventoryClickEvent grab = clickAt(RAW_SOURCE_SLOT, InventoryAction.PICKUP_ALL, null, -1);

        assertThat(ShulkerMenuPolicy.clickBlocked(grab, 0)).isTrue();
    }

    @Test
    void nestingAShulkerIntoTheViewIsPrevented() {
        // Placing a shulker box onto a view slot from the cursor would nest a box inside a box, refused.
        InventoryClickEvent place = clickAt(3, InventoryAction.PLACE_ALL, new ItemStack(Material.LIME_SHULKER_BOX), -1);

        assertThat(ShulkerMenuPolicy.clickBlocked(place, 0)).isTrue();
    }

    @Test
    void editingAViewSlotIsAllowed() {
        // A plain item click on a view slot (empty cursor, no hotbar swap) is the player's to make.
        InventoryClickEvent edit = clickAt(4, InventoryAction.PICKUP_ALL, null, -1);

        assertThat(ShulkerMenuPolicy.clickBlocked(edit, 0)).isFalse();
    }

    @Test
    void aDisabledSubFeatureIsANoOp() {
        grantPermission();
        config.put("shulkers.enabled", false);
        holdShulkerContaining(0, new ItemStack(Material.DIAMOND, 3));

        rightClickHeldItem();

        assertThat(nothingOpened()).isTrue();
        assertThat(view.openCount()).isZero();
    }

    @Test
    void aPlayerWithoutPermissionIsANoOp() {
        holdShulkerContaining(0, new ItemStack(Material.DIAMOND, 3)); // permission not granted

        rightClickHeldItem();

        assertThat(nothingOpened()).isTrue();
    }

    @Test
    void aNonShulkerItemDoesNothing() {
        grantPermission();
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));

        rightClickHeldItem();

        assertThat(nothingOpened()).isTrue();
    }

    private void grantPermission() {
        player.addAttachment(plugin, PERMISSION, true);
    }

    private void holdShulkerContaining(int slot, ItemStack content) {
        ItemStack box = new ItemStack(Material.RED_SHULKER_BOX);
        BlockStateMeta meta = (BlockStateMeta) box.getItemMeta();
        ShulkerBox state = (ShulkerBox) meta.getBlockState();
        state.getInventory().setItem(slot, content);
        meta.setBlockState(state);
        box.setItemMeta(meta);
        player.getInventory().setItemInMainHand(box);
    }

    /** Drive the interact handler directly: MockBukkit does not route {@code PlayerInteractEvent} through callEvent. */
    private void rightClickHeldItem() {
        ItemStack hand = player.getInventory().getItemInMainHand();
        listener.onInteract(new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_AIR, hand, null, BlockFace.SELF, EquipmentSlot.HAND));
    }

    private boolean nothingOpened() {
        Inventory top = player.getOpenInventory().getTopInventory();
        return top == null || !(top.getHolder() instanceof ShulkerBoxHolder);
    }

    /**
     * A mocked click over a 27-slot top view. MockBukkit cannot construct or dispatch a real
     * {@link InventoryClickEvent} in this version, so the click's raw slot, action, cursor and hotbar button are
     * stubbed directly to exercise the dupe-safe {@link ShulkerMenuPolicy} lock.
     */
    private static InventoryClickEvent clickAt(
            int rawSlot, InventoryAction action, ItemStack cursor, int hotbarButton) {
        Inventory top = mock(Inventory.class);
        when(top.getSize()).thenReturn(27);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getHotbarButton()).thenReturn(hotbarButton);
        when(event.getAction()).thenReturn(action);
        when(event.getCursor()).thenReturn(cursor);
        return event;
    }

    private static ItemStack[] boxContents(ItemStack box) {
        BlockStateMeta meta = (BlockStateMeta) box.getItemMeta();
        return ((ShulkerBox) meta.getBlockState()).getInventory().getContents();
    }

    /** A map-backed {@link ConfigStore} scoped to {@code modules.itemworld} so a test flips the shulker flags. */
    private static final class MutableConfig implements ConfigStore {
        private final Map<String, Object> values = new HashMap<>();

        void put(String path, Object value) {
            values.put(path, value);
        }

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }
    }

    /** Resolves a title key to its own string; MiniMessage parses it as literal text in the view. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Runs scheduled work inline so the disable-time flush write-back is observable without ticking Folia. */
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
