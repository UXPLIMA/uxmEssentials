package com.uxplima.uxmessentials.invrollback.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.invrollback.domain.RetentionPolicy;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage of {@link PruneSnapshots} driving a {@link RetentionPolicy} through the repository: the sweep
 * applies the per-player count cap across every owner ({@code deleteBeyondCount}) and the global age cutoff
 * ({@code deleteOlderThan}), so a table with over-count and over-age rows is trimmed to the newest, in-window set;
 * disabled limits (zero) prune nothing.
 */
class PruneSnapshotsTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void theSweepRemovesOverCountAndOverAgeSnapshotsAcrossEveryOwner() {
        FakeRepository repository = new FakeRepository();
        // Alice has four recent snapshots, over the count cap of two.
        repository.save(snapshot(ALICE, NOW.minusSeconds(40)));
        repository.save(snapshot(ALICE, NOW.minusSeconds(30)));
        repository.save(snapshot(ALICE, NOW.minusSeconds(20)));
        repository.save(snapshot(ALICE, NOW.minusSeconds(10)));
        // Bob has one recent (kept) and one ancient (over the age cap) snapshot.
        repository.save(snapshot(BOB, NOW.minusSeconds(5)));
        repository.save(snapshot(BOB, NOW.minus(Duration.ofDays(45))));

        int removed = new PruneSnapshots(repository, new RetentionPolicy(2, 30)).sweep(NOW);

        // Alice: 4 -> 2 (two oldest pruned). Bob: the ancient one pruned by age -> 1.
        assertThat(removed).isEqualTo(3);
        assertThat(repository.list(ALICE))
                .extracting(Snapshot::createdAt)
                .containsExactly(NOW.minusSeconds(10), NOW.minusSeconds(20));
        assertThat(repository.list(BOB)).extracting(Snapshot::createdAt).containsExactly(NOW.minusSeconds(5));
    }

    @Test
    void disabledLimitsPruneNothing() {
        FakeRepository repository = new FakeRepository();
        repository.save(snapshot(ALICE, NOW.minus(Duration.ofDays(365))));
        repository.save(snapshot(ALICE, NOW.minusSeconds(1)));

        int removed = new PruneSnapshots(repository, new RetentionPolicy(0, 0)).sweep(NOW);

        assertThat(removed).isZero();
        assertThat(repository.list(ALICE)).hasSize(2);
    }

    private static Snapshot snapshot(UUID owner, Instant createdAt) {
        return Snapshot.of(SnapshotId.random(), owner, SnapshotCause.DEATH, createdAt, new byte[] {1});
    }

    /** An in-memory {@link SnapshotRepository} implementing the two prune primitives the way the jOOQ adapter does. */
    private static final class FakeRepository implements SnapshotRepository {
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
