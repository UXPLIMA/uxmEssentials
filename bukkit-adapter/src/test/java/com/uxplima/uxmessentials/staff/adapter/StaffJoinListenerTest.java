package com.uxplima.uxmessentials.staff.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.RecordingRepository;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.RecordingVanish;
import com.uxplima.uxmessentials.staff.adapter.inbound.listener.StaffJoinListener;
import com.uxplima.uxmessentials.staff.adapter.outbound.BukkitStaffLoadoutCapture;
import com.uxplima.uxmessentials.staff.adapter.outbound.StaffModeStoreImpl;
import com.uxplima.uxmessentials.staff.application.EnterStaffMode;
import com.uxplima.uxmessentials.staff.application.ExitStaffMode;
import com.uxplima.uxmessentials.staff.application.RecoverStaffLoadout;
import com.uxplima.uxmessentials.staff.application.SendStaffChat;
import com.uxplima.uxmessentials.staff.application.port.StaffChannel;
import com.uxplima.uxmessentials.staff.application.port.StaffInspector;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The crash-recovery join listener: a player who rejoins with an orphaned loadout row (the in-memory marker lost
 * to a crash) has their real loadout restored and the row deleted on join, BEFORE they can reach {@code
 * /staffmode}. A player with no orphaned row, or one with a live active marker, is left untouched.
 */
class StaffJoinListenerTest {

    private ServerMock server;
    private Player player;
    private PlayerRef who;
    private StaffSettings settings;
    private StaffGadgetItems gadgetItems;
    private StaffModeStoreImpl store;
    private RecordingRepository repository;
    private RecordingVanish vanish;
    private BukkitStaffLoadoutCapture capture;
    private StaffServices services;
    private StaffJoinListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Alice");
        who = new PlayerRef(player.getUniqueId(), player.getName());
        Plugin plugin = MockBukkit.createMockPlugin("uxmEssentials");
        settings = StaffAdapterFakes.defaultSettings();
        gadgetItems = new StaffGadgetItems(plugin);
        store = new StaffModeStoreImpl();
        repository = new RecordingRepository();
        vanish = new RecordingVanish();
        capture = new BukkitStaffLoadoutCapture(settings, gadgetItems, vanish);
        RecoverStaffLoadout recover =
                new RecoverStaffLoadout(store, repository, capture, vanish, StaffAdapterFakes.notifier());
        EnterStaffMode enter = new EnterStaffMode(
                store,
                repository,
                capture,
                vanish,
                StaffAdapterFakes.notifier(),
                new StaffAdapterFakes.RecordingEvents(),
                recover,
                "default",
                true);
        ExitStaffMode exit = new ExitStaffMode(
                store,
                repository,
                capture,
                vanish,
                StaffAdapterFakes.notifier(),
                new StaffAdapterFakes.RecordingEvents());
        SendStaffChat chat = new SendStaffChat(StaffChannel.NONE, new StaffAdapterFakes.RecordingEvents());
        services = new StaffServices(enter, exit, recover, chat, StaffInspector.NONE, store);
        listener = new StaffJoinListener(services, repository, new SyncScheduler());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anOrphanedRowOnJoinIsRestoredAndDeleted() {
        // The player rejoins holding the gadget hotbar; the real sword is in the orphaned row.
        repository.rows.put(who.uuid(), swordLoadout());
        player.getInventory().clear();

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        // The real sword is back on the player and the row is gone: the interrupted exit is finished.
        assertThat(player.getInventory().getItem(0)).isNotNull();
        assertThat(player.getInventory().getItem(0).getType()).isEqualTo(Material.DIAMOND_SWORD);
        assertThat(repository.rows).doesNotContainKey(who.uuid());
    }

    @Test
    void noOrphanedRowOnJoinDoesNothing() {
        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        // No row to recover: the listener does not even schedule a restore.
        assertThat(repository.calls).containsExactly("load");
    }

    @Test
    void aLiveActiveMarkerOnJoinIsNotDisturbed() {
        // An active marker means this is a live session (not a crash orphan); the listener must leave it alone.
        store.setActive(who, "default");
        repository.rows.put(who.uuid(), swordLoadout());

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertThat(repository.rows).containsKey(who.uuid());
    }

    private SavedLoadout swordLoadout() {
        Player probe = server.addPlayer("Probe");
        probe.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
        // The capture is a pure value; the probe is only used to produce the encoded sword loadout the orphaned
        // row would hold.
        return capture.capture(new PlayerRef(probe.getUniqueId(), probe.getName()));
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
