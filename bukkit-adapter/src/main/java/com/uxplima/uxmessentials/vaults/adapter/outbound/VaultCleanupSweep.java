package com.uxplima.uxmessentials.vaults.adapter.outbound;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.vaults.application.PurgeInactiveVaults;
import com.uxplima.uxmessentials.vaults.application.port.VaultAudit;
import org.jspecify.annotations.NullMarked;

/**
 * The inactive-vault cleanup sweep: a self-rescheduling async loop that deletes every vault untouched for at
 * least the configured window, reclaiming storage held by players who long ago abandoned a vault. It mirrors
 * the mail-expiry sweep: it runs off the tick thread (the delete is a bounded {@code DELETE ... WHERE
 * last_touched < ?} on the indexed column), observes the module's {@code running} flag so it stops cleanly on
 * disable, and arms its first sweep one interval out so enable is never blocked. The §6.10 self-rescheduling
 * loop pattern, on the kernel {@link Scheduler} port, never a {@code BukkitScheduler}.
 *
 * <p>A sweep that actually removes rows logs the count and writes one {@code event=vault_purge} audit line, so
 * the reclaim is traceable; a sweep that finds nothing to purge is silent. The sweep is opt-in: the wiring only
 * arms it when {@code cleanup.enabled} is set, so it ships off by default and an operator turns it on
 * deliberately.
 */
@NullMarked
public final class VaultCleanupSweep {

    private final Scheduler scheduler;
    private final PurgeInactiveVaults purge;
    private final VaultAudit audit;
    private final Duration interval;
    private final Duration inactiveFor;
    private final BooleanSupplier running;
    private final Logger log;
    private final Clock clock;

    public VaultCleanupSweep(
            Scheduler scheduler,
            PurgeInactiveVaults purge,
            VaultAudit audit,
            Duration interval,
            Duration inactiveFor,
            BooleanSupplier running,
            Logger log,
            Clock clock) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.purge = Objects.requireNonNull(purge, "purge");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.interval = Objects.requireNonNull(interval, "interval");
        this.inactiveFor = Objects.requireNonNull(inactiveFor, "inactiveFor");
        this.running = Objects.requireNonNull(running, "running");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Arm the first sweep; subsequent sweeps reschedule themselves until the module stops. */
    public void start() {
        scheduleNext();
    }

    private void scheduleNext() {
        if (!running.getAsBoolean()) {
            return;
        }
        scheduler.asyncAfter(interval, this::sweepOnce);
    }

    private void sweepOnce() {
        if (!running.getAsBoolean()) {
            return;
        }
        try {
            purgeInactive();
        } catch (RuntimeException failure) {
            // A throwing purge must not skip the reschedule below, or cleanup would stop reclaiming until a reload.
            log.error("vault cleanup sweep failed", failure);
        }
        scheduleNext();
    }

    private void purgeInactive() {
        int removed = purge.purge(inactiveFor, clock.instant());
        if (removed > 0) {
            log.info("vault cleanup sweep purged {} inactive vault(s)", removed);
            audit.purged(removed);
        }
    }
}
