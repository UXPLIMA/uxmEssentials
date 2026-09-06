package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.playerstate.application.port.AfkStatus;
import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The AFK-aware playtime sampler: on a fixed interval it credits each online player the interval's seconds to
 * today's row in the {@link PlaytimeRepository}, as active or AFK time depending on the player's live
 * {@link AfkStatus}. This is the periodic engine behind the DB-backed {@code /playtime} breakdown; the Bukkit
 * lifetime {@code play-one-minute} statistic keeps ticking independently for continuity.
 *
 * <p>It uses the same self-rescheduling loop as {@code economy}'s {@code SalaryTask} and {@code presence}'s
 * {@code AfkSweep}: {@link Scheduler#asyncAfter} re-arms the loop, a {@code volatile} {@code running} flag flips
 * false on stop so the next tick exits, and the in-flight async writes are short and idempotent-keyed so a reload
 * cannot corrupt a row.
 *
 * <h2>Concurrency</h2>
 * Ownership: the {@code running} flag is the only mutable state and is <b>volatile</b>. Each tick reads the online
 * roster on the <b>global region thread</b> (the roster is global game state Folia forbids reading off-thread) via
 * the injected {@code roster} supplier, snapshots the refs, then writes each player's delta <b>async</b> off the
 * tick thread. No Bukkit API is touched off the global thread, and no write blocks a tick thread.
 *
 * <p>Day boundary: each tick stamps the seconds onto the server's current local date (the {@code clock}'s zone).
 * A session that spans midnight naturally lands its post-midnight seconds in the new day's row on the next sample
 * there is no rollover step; the per-day rows make that automatic.
 */
@NullMarked
public final class PlaytimeSampler {

    private final Scheduler scheduler;
    private final PlaytimeRepository repository;
    private final AfkStatus afkStatus;
    private final Supplier<List<PlayerRef>> roster;
    private final Clock clock;
    private final ZoneId zone;

    private final boolean enabled;
    private final Duration interval;
    private final long intervalSeconds;

    private volatile boolean running;

    public PlaytimeSampler(
            Scheduler scheduler,
            PlaytimeRepository repository,
            AfkStatus afkStatus,
            Supplier<List<PlayerRef>> roster,
            Clock clock,
            boolean enabled,
            Duration interval) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.afkStatus = Objects.requireNonNull(afkStatus, "afkStatus");
        this.roster = Objects.requireNonNull(roster, "roster");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = clock.getZone();
        this.enabled = enabled;
        this.interval = Objects.requireNonNull(interval, "interval");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive: " + interval);
        }
        this.intervalSeconds = interval.toSeconds();
    }

    /** Arm the sampling loop. A no-op when sampling is disabled in config. */
    public void start() {
        if (!enabled) {
            return;
        }
        running = true;
        scheduleNext();
    }

    /** Flip the loop off; the next scheduled tick observes the flag and exits without rescheduling. */
    public void stop() {
        running = false;
    }

    private void scheduleNext() {
        if (!running) {
            return;
        }
        scheduler.asyncAfter(interval, this::tick);
    }

    private void tick() {
        if (!running) {
            return;
        }
        // The online roster is global game state; enumerate it on the global region thread, snapshot the refs,
        // then credit each one off-tick. AFK state is read async per-player from the presence-backed seam.
        scheduler.onGlobal(() -> {
            if (!running) {
                return;
            }
            List<PlayerRef> online = roster.get();
            LocalDate day = LocalDate.now(clock.withZone(zone));
            for (PlayerRef ref : online) {
                scheduler.async(() -> sampleOne(ref, day));
            }
            scheduleNext();
        });
    }

    private void sampleOne(PlayerRef ref, LocalDate day) {
        if (afkStatus.isAfk(ref)) {
            repository.addSeconds(ref.uuid(), day, 0L, intervalSeconds);
        } else {
            repository.addSeconds(ref.uuid(), day, intervalSeconds, 0L);
        }
    }
}
