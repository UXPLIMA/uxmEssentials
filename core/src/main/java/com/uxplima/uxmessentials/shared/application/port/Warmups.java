package com.uxplima.uxmessentials.shared.application.port;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for teleport warmups: a countdown the player can cancel by moving.
 *
 * <p>A warmup is in-flight runtime state, not a stamp: nothing is written to PDC, and the owning
 * module drops every pending warmup on {@code stop()}. The duration is resolved from numbered
 * permission nodes via {@link Permissions} with the {@code min}-reducer (lowest matching
 * {@code uxmessentials.<feature>.warmup.<seconds>} wins, {@code 0} removes the warmup); the
 * {@code uxmessentials.<feature>.warmup.bypass} node starts the teleport immediately, immune to
 * move-cancel.
 *
 * <p>The move-cancels-warmup invariant itself is owned by the teleport context (its
 * {@code PlayerMoveEvent} listener cancels a pending warmup whose origin block changed). This port is
 * the primitive that context builds on: {@link #begin} resolves the duration, schedules the countdown
 * through the injected {@link Scheduler}, and invokes {@code onComplete} on the player's region thread
 * when the countdown elapses without a cancel. A zero-second warmup (bypass or default) completes
 * synchronously without scheduling.
 */
public interface Warmups {

    /**
     * Begin a warmup for {@code who} on {@code kind}; on elapse run {@code onComplete}, and if the
     * warmup is cancelled run {@code onCancel}. Returns a {@link WarmupHandle} whose
     * {@link WarmupHandle#cancel()} the owning context calls when the player moves. A duration of zero
     * runs {@code onComplete} immediately and returns an already-completed handle.
     */
    WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel);

    /**
     * A tiered warmup's identity: the feature segment used to build the permission nodes and the
     * config-default duration in seconds when the player holds no matching tier node.
     *
     * @param feature the node segment, e.g. {@code tp} → {@code uxmessentials.tp.warmup.<seconds>}
     * @param defaultSeconds the config fallback in seconds when no tier node matches
     */
    record WarmupKind(String feature, long defaultSeconds) {

        public WarmupKind {
            if (feature == null || feature.isBlank()) {
                throw new IllegalArgumentException("feature must be non-blank");
            }
            if (defaultSeconds < 0) {
                throw new IllegalArgumentException("defaultSeconds must be >= 0: " + defaultSeconds);
            }
        }

        /** The permission node prefix this kind resolves its tier against. */
        public String warmupNode() {
            return "uxmessentials." + feature + ".warmup";
        }

        /** The bypass node that starts the teleport immediately, immune to move-cancel. */
        public String bypassNode() {
            return warmupNode() + ".bypass";
        }

        /** The default duration as a {@link Duration}. */
        public Duration defaultDuration() {
            return Duration.ofSeconds(defaultSeconds);
        }
    }

    /**
     * A handle to one in-flight warmup. The owning context cancels it when its move/damage listener
     * fires; cancellation is idempotent and is a no-op once the warmup has completed. The handle holds
     * no scheduler resource. The countdown re-checks its own cancelled flag rather than carrying a
     * cancellable scheduled handle (the {@link Scheduler} port is fire-and-forget).
     */
    interface WarmupHandle {

        /** Request cancellation; idempotent, and a no-op after completion. */
        void cancel();

        /** True once the countdown has elapsed and {@code onComplete} has run. */
        boolean isComplete();

        /** True once {@link #cancel()} has taken effect before completion. */
        boolean isCancelled();
    }

    /**
     * A handle for a warmup that never began because something outside the plugin refused the action.
     *
     * <p>It reports itself as cancelled rather than complete, which is what it is: nothing counted down and nothing
     * will run. A caller that already handles a move-cancelled warmup therefore handles this too.
     */
    final class RefusedWarmup implements WarmupHandle {

        @Override
        public void cancel() {
            // Already refused; there is nothing in flight to stop.
        }

        @Override
        public boolean isComplete() {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    }

    /** A handle for the zero-duration case, already complete and never cancellable. */
    final class CompletedWarmup implements WarmupHandle {

        private final PlayerRef who;

        public CompletedWarmup(PlayerRef who) {
            this.who = Objects.requireNonNull(who, "who");
        }

        /** The player this warmup completed for. */
        public PlayerRef player() {
            return who;
        }

        @Override
        public void cancel() {
            // Already complete; nothing to cancel.
        }

        @Override
        public boolean isComplete() {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}
