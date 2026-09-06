package com.uxplima.uxmessentials.vanish.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryVanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@link VanishSilentContainerListener}: a vanished player's open of a lidded container is
 * cancelled, suppressing the noisy vanilla open, and re-shown as a detached silent copy, while a visible player, a
 * disabled toggle, or a container that does not animate is left to open normally.
 */
class VanishSilentContainerListenerTest {

    private ServerMock server;
    private InMemoryVanishStore store;
    private CapturingScheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        store = new InMemoryVanishStore();
        scheduler = new CapturingScheduler();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aVanishedPlayersChestOpenIsSilencedThenReshownAsACopy() {
        PlayerMock alice = server.addPlayer("Alice");
        store.vanish(alice.getUniqueId(), VanishLevel.DEFAULT);
        Inventory chest = Bukkit.createInventory(null, InventoryType.CHEST);
        InventoryOpenEvent event = openEvent(alice, chest);

        listener(true).onOpen(event);

        assertThat(event.isCancelled()).isTrue(); // the noisy vanilla open is suppressed
        scheduler.runCaptured(); // the deferred silent re-open lands next tick
        assertThat(alice.getOpenInventory().getTopInventory()).isNotSameAs(chest); // now viewing the detached copy
    }

    @Test
    void aVisiblePlayersChestOpenIsUntouched() {
        PlayerMock bob = server.addPlayer("Bob"); // not vanished
        InventoryOpenEvent event = openEvent(bob, Bukkit.createInventory(null, InventoryType.CHEST));

        listener(true).onOpen(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void nothingIsSilencedWhenSilentChestsOff() {
        PlayerMock alice = server.addPlayer("Alice");
        store.vanish(alice.getUniqueId(), VanishLevel.DEFAULT);
        InventoryOpenEvent event = openEvent(alice, Bukkit.createInventory(null, InventoryType.CHEST));

        listener(false).onOpen(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void aVanishedPlayersRealBlockChestIsStillSilenced() {
        PlayerMock alice = server.addPlayer("Alice");
        store.vanish(alice.getUniqueId(), VanishLevel.DEFAULT);
        World world = server.getWorld("world");
        Block block = world.getBlockAt(1, 64, 1);
        block.setType(Material.CHEST);
        Inventory chest = ((Container) block.getState()).getInventory();
        InventoryOpenEvent event = openEvent(alice, chest);

        listener(true).onOpen(event);

        assertThat(event.isCancelled()).isTrue(); // a real block chest carries a world location, so it is silenced
    }

    @Test
    void aVanishedPlayersVirtualGuiOpenIsNotMirrored() {
        // A plugin GUI (the menu engine, the PIN keypad) is a virtual CHEST inventory with no block, so getLocation()
        // is null. Mirroring it would swap it for a null-holder copy and strip its MenuHolder, which silently breaks
        // click-cancelling; the listener must leave a virtual GUI alone even though its type is CHEST.
        PlayerMock alice = server.addPlayer("Alice");
        store.vanish(alice.getUniqueId(), VanishLevel.DEFAULT);
        Inventory virtualChestGui = mock(Inventory.class);
        when(virtualChestGui.getType()).thenReturn(InventoryType.CHEST);
        when(virtualChestGui.getLocation()).thenReturn(null);
        InventoryView view = mock(InventoryView.class);
        when(view.getPlayer()).thenReturn(alice);
        when(view.getTopInventory()).thenReturn(virtualChestGui);
        InventoryOpenEvent event = new InventoryOpenEvent(view);

        listener(true).onOpen(event);

        assertThat(event.isCancelled()).isFalse(); // a virtual GUI carries no block location, so it is never mirrored
    }

    @Test
    void aNonLiddedContainerOpenIsNotSilenced() {
        PlayerMock alice = server.addPlayer("Alice");
        store.vanish(alice.getUniqueId(), VanishLevel.DEFAULT);
        InventoryOpenEvent event = openEvent(alice, Bukkit.createInventory(null, InventoryType.HOPPER));

        listener(true).onOpen(event);

        assertThat(event.isCancelled()).isFalse(); // a hopper animates nothing, so it is already silent
    }

    private VanishSilentContainerListener listener(boolean silentChests) {
        return new VanishSilentContainerListener(store, scheduler, server, silentChests);
    }

    private static InventoryOpenEvent openEvent(PlayerMock player, Inventory inventory) {
        InventoryView view = player.openInventory(inventory);
        return new InventoryOpenEvent(view);
    }

    /** A scheduler that captures the last per-entity task so the test can run the deferred re-open on demand. */
    private static final class CapturingScheduler implements Scheduler {
        private @Nullable Runnable captured;

        void runCaptured() {
            if (captured != null) {
                captured.run();
            }
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            captured = task;
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
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
