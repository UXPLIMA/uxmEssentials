package com.uxplima.uxmessentials.invrollback.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The pure decision of which of a player's snapshots to prune so the table stays bounded. Two independent limits
 * apply, and a snapshot is pruned if it fails <em>either</em>: a per-player count cap (keep only the newest
 * {@code maxPerPlayer}) and a maximum age (drop anything older than {@code maxAgeDays}). Either limit is disabled
 * by setting it to {@code 0} (or negative), so an operator can bound by count only, by age only, both, or
 * neither.
 *
 * <p>No I/O, no clock of its own. The caller passes the reference instant, which keeps the rule deterministic
 * and trivially testable. The adapter's scheduled sweep feeds it the player's snapshots and applies its verdict.
 *
 * @param maxPerPlayer the most snapshots to keep per player, or {@code <= 0} for no count limit
 * @param maxAgeDays the greatest age in days a snapshot may reach, or {@code <= 0} for no age limit
 */
public record RetentionPolicy(int maxPerPlayer, int maxAgeDays) {

    /**
     * The snapshots from {@code snapshots} that should be pruned as of {@code now}. Those beyond the newest
     * {@code maxPerPlayer}, plus those older than {@code maxAgeDays}. The returned list preserves newest-first
     * order and never contains a snapshot that should be kept.
     */
    public List<Snapshot> selectForPruning(List<Snapshot> snapshots, Instant now) {
        Objects.requireNonNull(snapshots, "snapshots");
        Objects.requireNonNull(now, "now");
        List<Snapshot> newestFirst = new ArrayList<>(snapshots);
        newestFirst.sort(Comparator.comparing(Snapshot::createdAt).reversed());
        Instant ageCutoff = maxAgeDays > 0 ? now.minus(Duration.ofDays(maxAgeDays)) : null;
        List<Snapshot> pruned = new ArrayList<>();
        for (int rank = 0; rank < newestFirst.size(); rank++) {
            Snapshot snapshot = newestFirst.get(rank);
            boolean overCount = maxPerPlayer > 0 && rank >= maxPerPlayer;
            boolean overAge = ageCutoff != null && snapshot.createdAt().isBefore(ageCutoff);
            if (overCount || overAge) {
                pruned.add(snapshot);
            }
        }
        return List.copyOf(pruned);
    }
}
