package com.uxplima.uxmessentials.invrollback.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Unit coverage of the pure {@link RetentionPolicy} prune decision: an over-count set keeps the newest N and
 * prunes the oldest surplus; an over-age set prunes anything past the maximum age regardless of count; the two
 * limits union; and either limit set to zero is disabled.
 */
class RetentionPolicyTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void overCountPrunesTheOldestSurplusAndKeepsTheNewestN() {
        List<Snapshot> snapshots = List.of(
                at(NOW.minusSeconds(50)),
                at(NOW.minusSeconds(40)),
                at(NOW.minusSeconds(30)),
                at(NOW.minusSeconds(20)),
                at(NOW.minusSeconds(10)));

        List<Snapshot> pruned = new RetentionPolicy(3, 0).selectForPruning(snapshots, NOW);

        // Keep the newest 3 (t-10, t-20, t-30); prune the two oldest (t-40, t-50).
        assertThat(pruned).extracting(Snapshot::createdAt).containsExactly(NOW.minusSeconds(40), NOW.minusSeconds(50));
    }

    @Test
    void overAgePrunesEverythingOlderThanTheCutoffAndKeepsTheRest() {
        Snapshot fresh = at(NOW.minus(Duration.ofDays(10)));
        Snapshot borderline = at(NOW.minus(Duration.ofDays(30))); // exactly the cutoff, kept (not strictly older)
        Snapshot stale = at(NOW.minus(Duration.ofDays(31)));
        Snapshot ancient = at(NOW.minus(Duration.ofDays(90)));

        List<Snapshot> pruned =
                new RetentionPolicy(0, 30).selectForPruning(List.of(fresh, borderline, stale, ancient), NOW);

        assertThat(pruned).containsExactly(stale, ancient);
    }

    @Test
    void countAndAgeLimitsUnion() {
        Snapshot newest = at(NOW.minusSeconds(5));
        Snapshot recent = at(NOW.minusSeconds(10));
        Snapshot old = at(NOW.minus(Duration.ofDays(40))); // beyond the count cap AND older than the age cutoff

        List<Snapshot> pruned = new RetentionPolicy(2, 30).selectForPruning(List.of(newest, recent, old), NOW);

        // The over-age snapshot is also the count-surplus one; it appears once, not twice.
        assertThat(pruned).containsExactly(old);
    }

    @Test
    void zeroLimitsDisableTheirCheck() {
        List<Snapshot> snapshots = List.of(at(NOW.minus(Duration.ofDays(365))), at(NOW.minusSeconds(1)));

        assertThat(new RetentionPolicy(0, 0).selectForPruning(snapshots, NOW)).isEmpty();
    }

    @Test
    void prunesNothingWhenUnderBothLimits() {
        List<Snapshot> snapshots = List.of(at(NOW.minusSeconds(5)), at(NOW.minusSeconds(10)));

        assertThat(new RetentionPolicy(5, 30).selectForPruning(snapshots, NOW)).isEmpty();
    }

    private static Snapshot at(Instant createdAt) {
        return Snapshot.of(SnapshotId.random(), OWNER, SnapshotCause.DEATH, createdAt, new byte[] {1, 2, 3});
    }
}
