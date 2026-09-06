package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.EconomyMaintenance;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The self-rescheduling economy data-maintenance loop (the {@link SalaryTask} pattern): on its interval it trims
 * stale transaction telemetry and purges the wallets of long-inactive players through {@link EconomyMaintenance},
 * all off the tick thread. Two safety properties make the destructive half safe: it never touches an owner in
 * {@link EconomyMaintenance#protectedOwners()} (a loan/score/bank tie), and it only ever purges an owner whose
 * last login is <em>known</em> and before the cutoff: an owner with no recorded last-played is left alone. When
 * {@code dry-run} is set (the default) it computes and logs what it <em>would</em> remove and deletes nothing, so
 * an operator can watch a server or two before arming the real purge.
 */
@NullMarked
public final class EconomyMaintenanceTask {

    /** Resolves a player's last-login epoch-millis (0 when unknown); the seam that keeps the task testable. */
    @FunctionalInterface
    public interface PlayerLastSeen {
        long lastSeenMillis(UUID uuid);
    }

    /** What one maintenance pass removed (or would remove, under dry-run). */
    public record Report(int prunedTransactions, int purgedOwners, boolean dryRun) {}

    private final EconomyMaintenance maintenance;
    private final Scheduler scheduler;
    private final Logger log;
    private final Clock clock;
    private final PlayerLastSeen lastSeen;

    private final boolean enabled;
    private final Duration interval;
    private final long purgeInactiveDays;
    private final long pruneTransactionDays;
    private final boolean dryRun;
    private volatile boolean running;

    public EconomyMaintenanceTask(
            EconomyMaintenance maintenance,
            Scheduler scheduler,
            Logger log,
            Clock clock,
            PlayerLastSeen lastSeen,
            boolean enabled,
            Duration interval,
            long purgeInactiveDays,
            long pruneTransactionDays,
            boolean dryRun) {
        this.maintenance = Objects.requireNonNull(maintenance, "maintenance");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lastSeen = Objects.requireNonNull(lastSeen, "lastSeen");
        this.enabled = enabled;
        this.interval = Objects.requireNonNull(interval, "interval");
        this.purgeInactiveDays = purgeInactiveDays;
        this.pruneTransactionDays = pruneTransactionDays;
        this.dryRun = dryRun;
    }

    public void start() {
        if (!enabled) {
            return;
        }
        running = true;
        scheduleNext();
    }

    public void stop() {
        running = false;
    }

    private void scheduleNext() {
        if (running) {
            scheduler.asyncAfter(interval, this::tick);
        }
    }

    private void tick() {
        if (!running) {
            return;
        }
        scheduler.async(() -> {
            runOnce(dryRun);
            scheduleNext();
        });
    }

    /**
     * Run one maintenance pass and report what was (or, under {@code dryRun}, would be) removed. Safe to call
     * directly: this is the seam {@code /eco purge} previews through. Runs queries inline; callers schedule it
     * off-tick.
     */
    public Report runOnce(boolean dryRunPass) {
        long now = clock.millis();
        int pruned = prune(now, dryRunPass);
        int purged = purge(now, dryRunPass);
        log.info(
                "Economy maintenance ({}): {} stale telemetry rows, {} inactive wallets",
                dryRunPass ? "dry-run" : "applied",
                pruned,
                purged);
        return new Report(pruned, purged, dryRunPass);
    }

    private int prune(long now, boolean dryRunPass) {
        long cutoff = now - Duration.ofDays(pruneTransactionDays).toMillis();
        return dryRunPass ? maintenance.countTransactionsBefore(cutoff) : maintenance.deleteTransactionsBefore(cutoff);
    }

    private int purge(long now, boolean dryRunPass) {
        long cutoff = now - Duration.ofDays(purgeInactiveDays).toMillis();
        Set<UUID> protectedOwners = maintenance.protectedOwners();
        List<UUID> candidates = new ArrayList<>();
        for (PlayerRef owner : maintenance.allOwners()) {
            UUID uuid = owner.uuid();
            long seen = lastSeen.lastSeenMillis(uuid);
            if (!protectedOwners.contains(uuid) && seen > 0L && seen < cutoff) {
                candidates.add(uuid);
            }
        }
        return dryRunPass ? candidates.size() : maintenance.purgeOwners(candidates);
    }
}
