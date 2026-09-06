package com.uxplima.uxmessentials.shared.application.port;

import java.time.Duration;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Folia-aware scheduling port. Application and use-case code schedule every piece of work through
 * this contract; the adapter dispatches each call to the right Folia scheduler
 * ({@code GlobalRegionScheduler} / {@code RegionScheduler} / {@code EntityScheduler} /
 * {@code AsyncScheduler}). The legacy {@code BukkitScheduler} is never used: that assumption (one
 * main thread owns every entity and world) is exactly what Folia breaks.
 *
 * <p>Pick the most specific method: {@link #onEntity} for per-player work, {@link #onRegion} for
 * work bound to a block or location (chunk load for an RTP candidate, a setwarp block check),
 * {@link #onGlobal} only for genuinely global state ({@code /time}, {@code /weather}, a broadcast)
 * it serialises onto one thread and kills Folia's parallelism. Plan each flow to hop once, do the
 * work, and finish; do not ping-pong between schedulers.
 *
 * <p>The port is deliberately fire-and-forget. Every method returns {@code void} and there is no
 * cancellation handle. Production found callers either need no cancellation or model it in their own
 * state (the teleport context's warmup re-checks a {@code cancelled} flag each tick rather than
 * holding a handle), and the fire-and-forget shape is what lets a {@code FeatureModule} drain its own
 * in-flight work on {@code stop()} with no global scheduler holding orphaned tasks. A module that
 * needs to bound its outstanding work tracks it itself (a {@code Phaser} or an {@code AtomicInteger}).
 * If a future feature genuinely needs cancellation or repeating semantics, extend this port rather
 * than reaching for a raw Paper scheduler.
 */
public interface Scheduler {

    /** Run on the global region thread: global game state only; serialises, so use sparingly. */
    void onGlobal(Runnable task);

    /** Run on the region thread owning {@code position}, block, chunk, and location work. */
    void onRegion(Position position, Runnable task);

    /**
     * Run on the region thread owning {@code player}'s entity. Silently no-ops when the player is
     * offline (the Folia entity scheduler refuses a despawned entity); callers needing to wait for a
     * relog listen for the join instead.
     */
    void onEntity(PlayerRef player, Runnable task);

    /**
     * Run on the region thread owning {@code player}'s entity, invoking {@code retired} instead of
     * {@code task} if the entity is retired before the task can run (the player logs off or the
     * region unloads). A caller that blocks on the result of {@code task} needs this branch so it can
     * complete with a fallback rather than wait forever for a task the scheduler has silently dropped;
     * the plain {@link #onEntity(PlayerRef, Runnable)} keeps its fire-and-forget no-op semantics.
     *
     * <p>The default ignores {@code retired} and delegates to {@link #onEntity(PlayerRef, Runnable)},
     * so a scheduler that cannot observe retirement keeps the old behaviour; the Folia adapter overrides
     * it to wire the entity scheduler's retired callback through.
     */
    default void onEntity(PlayerRef player, Runnable task, Runnable retired) {
        onEntity(player, task);
    }

    /**
     * Whether the calling thread already owns the global region. I.e. {@link #onGlobal} work would
     * run on this very thread. A caller that marshals onto the global region and then blocks on the
     * result must check this first: scheduling and blocking from the global thread itself deadlocks,
     * because the scheduled task cannot run until the blocked caller returns.
     *
     * <p>The default returns {@code false} (assume off the owning thread, so the caller schedules as
     * before); the Folia adapter answers from the live region-ownership check.
     */
    default boolean onGlobalThread() {
        return false;
    }

    /**
     * Whether the calling thread already owns {@code player}'s entity region, i.e. {@link #onEntity}
     * work for this player would run on this very thread. Same deadlock guard as {@link #onGlobalThread}
     * for the per-entity case; defaults to {@code false} for the same reason.
     */
    default boolean ownsEntity(PlayerRef player) {
        return false;
    }

    /** Run off any tick thread. The task must not touch the Bukkit API. */
    void async(Runnable task);

    /** Run off any tick thread after {@code delay}, on a non-shared executor. */
    void asyncAfter(Duration delay, Runnable task);

    /**
     * Run {@code task} once on the global region thread after {@code delay}, which is how a caller states
     * "do this a tick or two from now" without holding a repeating task open for it. A delay shorter than one
     * tick still lands on the next tick, since a tick is the smallest step the server schedules in.
     *
     * <p>The default throws {@link UnsupportedOperationException} so a test stub that never defers anything can
     * implement {@link Scheduler} without boilerplate, the same way {@link #repeatGlobal} does.
     */
    default void laterGlobal(Duration delay, Runnable task) {
        throw new UnsupportedOperationException("laterGlobal is not implemented by this Scheduler stub; "
                + "override it in the FoliaScheduler adapter or in tests that call it");
    }

    /**
     * Schedule {@code task} to run on the global region thread at a fixed rate: first after
     * {@code initialDelay}, then every {@code period}. The returned handle cancels the repeating
     * task when closed; callers must hold the handle and call {@link AutoCloseable#close()} on
     * module stop (or reload) to prevent orphaned tasks after a disable.
     *
     * <p>The default throws {@link UnsupportedOperationException} so test stubs that do not use
     * repeating tasks can implement {@link Scheduler} without boilerplate. The Folia adapter
     * overrides this with the real {@code runAtFixedRate} call.
     *
     * <p>If a future feature genuinely needs per-entity or per-region repeating tasks, add
     * narrower overloads following the same pattern rather than generalising this one.
     */
    default AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
        throw new UnsupportedOperationException("repeatGlobal is not implemented by this Scheduler stub; "
                + "override it in the FoliaScheduler adapter or in tests that call it");
    }
}
