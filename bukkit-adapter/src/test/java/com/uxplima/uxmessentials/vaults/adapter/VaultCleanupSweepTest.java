package com.uxplima.uxmessentials.vaults.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.vaults.adapter.outbound.VaultCleanupSweep;
import com.uxplima.uxmessentials.vaults.application.PurgeInactiveVaults;
import com.uxplima.uxmessentials.vaults.application.port.VaultAudit;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Drives the self-rescheduling {@link VaultCleanupSweep} with a capturing scheduler so each sweep can be run by
 * hand. A sweep purges inactive vaults through the use case (the cutoff is {@code now - inactiveFor}), reschedules
 * itself, and writes one {@code vault_purge} audit line per sweep that actually removed rows. Flipping the
 * {@code running} flag false (module stop) ends the loop: a sweep after stop neither purges nor reschedules.
 */
class VaultCleanupSweepTest {

    private static final Duration INTERVAL = Duration.ofHours(24);
    private static final Duration INACTIVE = Duration.ofDays(30);

    @Test
    void anEnabledSweepPurgesInactiveVaultsRescedulesAndAudits() {
        CapturingScheduler scheduler = new CapturingScheduler();
        DeletingRepository repository = new DeletingRepository(3);
        RecordingAudit audit = new RecordingAudit();
        AtomicBoolean running = new AtomicBoolean(true);
        VaultCleanupSweep sweep = sweep(scheduler, repository, audit, running);

        sweep.start();
        // start() armed the first sweep one interval out: nothing has run yet.
        assertThat(repository.cutoffs).isEmpty();
        assertThat(scheduler.pending).isNotNull();

        scheduler.runPending(); // run the first sweep

        assertThat(repository.cutoffs).hasSize(1);
        assertThat(audit.purgedCounts).containsExactly(3);
        // The sweep rescheduled itself for the next interval.
        assertThat(scheduler.pending).isNotNull();
        assertThat(scheduler.lastDelay).isEqualTo(INTERVAL);
    }

    @Test
    void aSweepThatFindsNothingPurgesButWritesNoAudit() {
        CapturingScheduler scheduler = new CapturingScheduler();
        DeletingRepository repository = new DeletingRepository(0);
        RecordingAudit audit = new RecordingAudit();
        VaultCleanupSweep sweep = sweep(scheduler, repository, audit, new AtomicBoolean(true));

        sweep.start();
        scheduler.runPending();

        assertThat(repository.cutoffs).hasSize(1); // it still queried
        assertThat(audit.purgedCounts).isEmpty(); // but nothing was removed, so no audit line
    }

    @Test
    void stoppingTheModuleEndsTheLoop() {
        CapturingScheduler scheduler = new CapturingScheduler();
        DeletingRepository repository = new DeletingRepository(5);
        RecordingAudit audit = new RecordingAudit();
        AtomicBoolean running = new AtomicBoolean(true);
        VaultCleanupSweep sweep = sweep(scheduler, repository, audit, running);

        sweep.start();
        running.set(false); // module stop before the armed sweep fires

        scheduler.runPending(); // the armed task observes running=false and bails

        assertThat(repository.cutoffs).isEmpty(); // no purge
        assertThat(scheduler.pending).isNull(); // and no reschedule. The loop is dead
    }

    private VaultCleanupSweep sweep(
            CapturingScheduler scheduler, VaultRepository repository, VaultAudit audit, AtomicBoolean running) {
        return new VaultCleanupSweep(
                scheduler,
                new PurgeInactiveVaults(repository),
                audit,
                INTERVAL,
                INACTIVE,
                running::get,
                new NoopLogger(),
                Clock.systemUTC());
    }

    /** Captures the single pending {@code asyncAfter} task so the test drives each sweep by hand. */
    private static final class CapturingScheduler implements Scheduler {
        private @Nullable Runnable pending;
        private @Nullable Duration lastDelay;

        void runPending() {
            Runnable task = pending;
            pending = null;
            if (task != null) {
                task.run();
            }
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            this.lastDelay = delay;
            this.pending = task;
        }

        @Override
        public void async(Runnable task) {
            task.run();
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
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }
    }

    /** A repository whose {@code deleteUntouchedBefore} records the cutoff and returns a fixed deleted count. */
    private static final class DeletingRepository implements VaultRepository {
        private final int deleted;
        private final List<Instant> cutoffs = new ArrayList<>();

        DeletingRepository(int deleted) {
            this.deleted = deleted;
        }

        @Override
        public int deleteUntouchedBefore(Instant cutoff) {
            cutoffs.add(cutoff);
            return deleted;
        }

        @Override
        public Optional<Vault> find(VaultId id) {
            return Optional.empty();
        }

        @Override
        public List<Integer> ownedIndices(PlayerRef owner) {
            return List.of();
        }

        @Override
        public List<com.uxplima.uxmessentials.vaults.application.VaultSummary> summaries(PlayerRef owner) {
            return List.of();
        }

        @Override
        public int count(PlayerRef owner) {
            return 0;
        }

        @Override
        public void save(Vault vault) {}

        @Override
        public void delete(VaultId id) {}
    }

    private static final class RecordingAudit implements VaultAudit {
        private final List<Integer> purgedCounts = new ArrayList<>();

        @Override
        public void purged(int count) {
            purgedCounts.add(count);
        }

        @Override
        public void adminOpened(PlayerRef actor, PlayerRef owner, UUID ownerUuid, int index) {}

        @Override
        public void adminDeleted(PlayerRef actor, PlayerRef owner, UUID ownerUuid, int index) {}
    }

    private static final class NoopLogger implements Logger {
        private final AtomicInteger ignored = new AtomicInteger();

        @Override
        public void info(String message, Object... args) {
            ignored.incrementAndGet();
        }

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
