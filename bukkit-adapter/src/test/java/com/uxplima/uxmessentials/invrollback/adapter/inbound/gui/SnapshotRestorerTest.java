package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

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
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec;
import com.uxplima.uxmessentials.invrollback.application.CaptureSnapshot;
import com.uxplima.uxmessentials.invrollback.application.RestoreSnapshot;
import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
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
 * MockBukkit coverage of {@link SnapshotRestorer}: restoring a stored snapshot overwrites the target's live
 * inventory with the snapshot's contents AND first freezes the pre-restore inventory as a {@link
 * SnapshotCause#RESTORE} safety snapshot, so an accidental restore is itself undoable. A stale snapshot id leaves
 * the inventory untouched and writes no safety copy.
 */
class SnapshotRestorerTest {

    private static final Instant WHEN = Instant.parse("2026-07-17T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(WHEN, ZoneOffset.UTC);

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world"); // the restore safety-snapshot records the target's location
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void restoreOverwritesTheInventoryAndSafetySnapshotsThePreRestoreState() {
        PlayerMock target = server.addPlayer();
        PlayerMock staff = server.addPlayer();

        // A stored snapshot holding a stack of diamonds in slot 0.
        ItemStack[] snapshotContents = new ItemStack[target.getInventory().getContents().length];
        snapshotContents[0] = new ItemStack(Material.DIAMOND, 5);
        SnapshotRepository repository = new RecordingRepository();
        Snapshot stored = Snapshot.capture(
                target.getUniqueId(),
                SnapshotCause.DEATH,
                WHEN.minusSeconds(60),
                InventorySnapshotCodec.encode(snapshotContents, null));
        repository.save(stored);

        // The player is currently holding dirt in slot 0: this is the state that must be safety-snapshotted.
        target.getInventory().setItem(0, new ItemStack(Material.DIRT, 12));

        restorer(repository).restore(ref(staff), ref(target), stored.id());

        assertThat(target.getInventory().getItem(0)).isNotNull();
        assertThat(target.getInventory().getItem(0).getType()).isEqualTo(Material.DIAMOND);
        assertThat(target.getInventory().getItem(0).getAmount()).isEqualTo(5);

        List<Snapshot> safety = repository.list(target.getUniqueId()).stream()
                .filter(s -> s.cause() == SnapshotCause.RESTORE)
                .toList();
        assertThat(safety).hasSize(1);
        InventorySnapshotCodec.Decoded preRestore =
                InventorySnapshotCodec.decode(safety.get(0).contents());
        assertThat(preRestore.contents()[0]).isNotNull();
        assertThat(preRestore.contents()[0].getType()).isEqualTo(Material.DIRT);
        assertThat(preRestore.contents()[0].getAmount()).isEqualTo(12);
    }

    @Test
    void aStaleIdChangesNothingAndWritesNoSafetySnapshot() {
        PlayerMock target = server.addPlayer();
        PlayerMock staff = server.addPlayer();
        target.getInventory().setItem(0, new ItemStack(Material.DIRT, 12));
        SnapshotRepository repository = new RecordingRepository();

        restorer(repository).restore(ref(staff), ref(target), SnapshotId.random());

        assertThat(target.getInventory().getItem(0).getType()).isEqualTo(Material.DIRT);
        assertThat(repository.list(target.getUniqueId())).isEmpty();
    }

    @Test
    void restoringForAnOfflineTargetReportsTheOnlineRequirementAndChangesNothing() {
        PlayerMock staff = server.addPlayer();
        // The target is never connected: restore is the one verb that must write to a live inventory, so it refuses.
        PlayerRef offlineTarget = new PlayerRef(UUID.randomUUID(), "Ghost");
        SnapshotRepository repository = new RecordingRepository();
        CapturingSink sink = new CapturingSink();
        RestoreSnapshot restore = new RestoreSnapshot(repository, new CaptureSnapshot(repository, 0));

        new SnapshotRestorer(restore, inlineScheduler(), keyEchoMessages(), sink, CLOCK, event -> {})
                .restore(ref(staff), offlineTarget, SnapshotId.random());

        assertThat(sink.last).contains("invrollback.player-not-found");
        assertThat(repository.list(offlineTarget.uuid())).isEmpty();
    }

    private static SnapshotRestorer restorer(SnapshotRepository repository) {
        RestoreSnapshot restore = new RestoreSnapshot(repository, new CaptureSnapshot(repository, 0));
        return new SnapshotRestorer(restore, inlineScheduler(), keyEchoMessages(), noopSink(), CLOCK, event -> {});
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private static Messages keyEchoMessages() {
        return (viewer, key, placeholders) -> key.key();
    }

    private static MessageSink noopSink() {
        return (viewer, renderedText) -> {};
    }

    /** A {@link MessageSink} that keeps the last rendered string so a test can assert which line was delivered. */
    private static final class CapturingSink implements MessageSink {
        private String last = "";

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            last = renderedText;
        }
    }

    /** A scheduler whose entity/async hops run inline, so the restore completes within the test. */
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
