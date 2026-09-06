package com.uxplima.uxmessentials.invrollback.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec;
import com.uxplima.uxmessentials.invrollback.application.CaptureSnapshot;
import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
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
 * MockBukkit coverage of the invrollback capture path end-to-end. A death capture reads the player's live
 * inventory, serializes it, and (off the tick thread via the injected {@link Scheduler}) persists a snapshot whose
 * contents and cause round-trip through the repository and back into items via the {@link InventorySnapshotCodec}.
 * The logout capture is gated by config. With {@code capture.on-logout} off a quit stores nothing, with it on a
 * quit stores a LOGOUT snapshot. The ender chest rides along only when {@code include-enderchest} is set.
 */
class SnapshotCaptureListenerTest {

    private static final Instant WHEN = Instant.parse("2026-07-17T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(WHEN, ZoneOffset.UTC);

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world"); // so a captured player has a location with a world to record
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aDeathCapturesASnapshotWhoseContentsAndCauseRoundTripThroughTheRepository() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
        RecordingRepository repository = new RecordingRepository();
        SnapshotCaptureListener listener = listener(repository, true, true, false);

        listener.capture(player, SnapshotCause.DEATH);

        List<Snapshot> stored = repository.list(player.getUniqueId());
        assertThat(stored).hasSize(1);
        Snapshot snapshot = stored.get(0);
        assertThat(snapshot.cause()).isEqualTo(SnapshotCause.DEATH);
        assertThat(snapshot.createdAt()).isEqualTo(WHEN);

        InventorySnapshotCodec.Decoded decoded = InventorySnapshotCodec.decode(snapshot.contents());
        assertThat(decoded.contents()[0]).isNotNull();
        assertThat(decoded.contents()[0].getType()).isEqualTo(Material.DIAMOND);
        assertThat(decoded.contents()[0].getAmount()).isEqualTo(5);
        assertThat(decoded.enderChest()).isEmpty(); // include-enderchest off
    }

    @Test
    void logoutCaptureIsGatedByConfig() {
        PlayerMock player = server.addPlayer();
        RecordingRepository repository = new RecordingRepository();

        listener(repository, true, false, true).onQuit(quit(player));
        assertThat(repository.list(player.getUniqueId())).isEmpty();

        listener(repository, true, true, true).onQuit(quit(player));
        List<Snapshot> stored = repository.list(player.getUniqueId());
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).cause()).isEqualTo(SnapshotCause.LOGOUT);
    }

    @Test
    void theEnderChestIsCapturedOnlyWhenConfigured() {
        PlayerMock player = server.addPlayer();
        player.getEnderChest().setItem(0, new ItemStack(Material.EMERALD, 3));
        RecordingRepository repository = new RecordingRepository();

        listener(repository, true, true, true).capture(player, SnapshotCause.DEATH);

        Snapshot snapshot = repository.list(player.getUniqueId()).get(0);
        InventorySnapshotCodec.Decoded decoded = InventorySnapshotCodec.decode(snapshot.contents());
        assertThat(decoded.enderChest()[0]).isNotNull();
        assertThat(decoded.enderChest()[0].getType()).isEqualTo(Material.EMERALD);
        assertThat(decoded.enderChest()[0].getAmount()).isEqualTo(3);
    }

    private static SnapshotCaptureListener listener(
            SnapshotRepository repository, boolean onDeath, boolean onLogout, boolean includeEnderchest) {
        return new SnapshotCaptureListener(
                new CaptureSnapshot(repository, 0), inlineScheduler(), CLOCK, onDeath, onLogout, includeEnderchest);
    }

    private static PlayerQuitEvent quit(PlayerMock player) {
        return new PlayerQuitEvent(player, Component.text("bye"), PlayerQuitEvent.QuitReason.DISCONNECTED);
    }

    /** A scheduler whose {@code async} hop runs inline, so a capture completes within the test. */
    private static Scheduler inlineScheduler() {
        return new Scheduler() {
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
        };
    }

    /** An in-memory {@link SnapshotRepository} that records saves for assertion. */
    private static final class RecordingRepository implements SnapshotRepository {
        private final List<Snapshot> rows = new ArrayList<>();

        @Override
        public void save(Snapshot snapshot) {
            rows.add(snapshot);
        }

        @Override
        public List<Snapshot> list(UUID owner) {
            return rows.stream()
                    .filter(s -> s.owner().equals(owner))
                    .sorted(Comparator.comparing(Snapshot::createdAt).reversed())
                    .toList();
        }

        @Override
        public List<UUID> owners() {
            return rows.stream().map(Snapshot::owner).distinct().toList();
        }

        @Override
        public Optional<Snapshot> find(SnapshotId id) {
            return rows.stream().filter(s -> s.id().equals(id)).findFirst();
        }

        @Override
        public void delete(SnapshotId id) {
            rows.removeIf(s -> s.id().equals(id));
        }

        @Override
        public int deleteBeyondCount(UUID owner, int keep) {
            List<Snapshot> newestFirst = list(owner);
            List<Snapshot> stale =
                    newestFirst.size() > keep ? newestFirst.subList(keep, newestFirst.size()) : List.of();
            rows.removeAll(stale);
            return stale.size();
        }

        @Override
        public int deleteOlderThan(Instant cutoff) {
            int before = rows.size();
            rows.removeIf(s -> s.createdAt().isBefore(cutoff));
            return before - rows.size();
        }
    }
}
