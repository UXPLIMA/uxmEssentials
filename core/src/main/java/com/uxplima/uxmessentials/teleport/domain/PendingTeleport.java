package com.uxplima.uxmessentials.teleport.domain;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * One in-flight teleport warmup, modelling the <b>move-cancels-warmup invariant</b> the teleport context
 * owns. It pins the {@link #origin} block the player must not leave, the {@link Destination} the warmup
 * precedes, the cancel toggles, and a {@code cancelled} flag. Exactly the snapshot the adapter keeps in
 * its {@code ConcurrentHashMap<UUID, PendingTeleport>} and re-checks each tick before issuing the hop.
 *
 * <p>The decision logic is here and pure: {@link #onMovement(Position)} compares the new position's
 * block against {@link #origin} (sub-block jitter never cancels, via {@link Position#sameBlock}) and
 * {@link #onAction(WarmupCancelReason)} consults the toggles. Each returns the resulting state plus the
 * {@link WarmupCancelReason} when a cancel fired, so the use case knows whether to raise
 * {@code WarmupCancelled}. The flag is one-way: a cancelled warmup never un-cancels, which is what makes
 * the cancel-vs-fire race resolve to a single loser.
 */
public final class PendingTeleport {

    private final PlayerRef player;
    private final TeleportKind kind;
    private final Position origin;
    private final Destination destination;
    private final WarmupCancelToggles toggles;
    private final boolean cancelled;

    private PendingTeleport(
            PlayerRef player,
            TeleportKind kind,
            Position origin,
            Destination destination,
            WarmupCancelToggles toggles,
            boolean cancelled) {
        this.player = player;
        this.kind = kind;
        this.origin = origin;
        this.destination = destination;
        this.toggles = toggles;
        this.cancelled = cancelled;
    }

    /** Arm a fresh, uncancelled warmup anchored at the player's current block. */
    public static PendingTeleport arm(
            PlayerRef player,
            TeleportKind kind,
            Position origin,
            Destination destination,
            WarmupCancelToggles toggles) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(toggles, "toggles");
        return new PendingTeleport(player, kind, origin, destination, toggles, false);
    }

    /**
     * Reconcile a movement: when move-cancel is armed and the player drifted off the origin block <em>and</em>
     * past the configured move threshold, this warmup cancels with {@link WarmupCancelReason#MOVED}. Sub-block
     * jitter (same block cell) never cancels, and with a non-zero threshold a small drift across a block edge
     * is tolerated too, so mouse jitter near a boundary does not abort the warmup.
     */
    public Outcome onMovement(Position to) {
        Objects.requireNonNull(to, "to");
        if (cancelled || !toggles.cancelOnMove() || origin.sameBlock(to)) {
            return Outcome.unchanged(this);
        }
        if (!toggles.movePassesThreshold(origin.distanceTo(to))) {
            return Outcome.unchanged(this);
        }
        return Outcome.cancelled(markCancelled(), WarmupCancelReason.MOVED);
    }

    /** Reconcile a non-move action (rotation, damage, explicit abort) against the toggles. */
    public Outcome onAction(WarmupCancelReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (cancelled || !toggles.arms(reason)) {
            return Outcome.unchanged(this);
        }
        return Outcome.cancelled(markCancelled(), reason);
    }

    /** True once this warmup has been cancelled and no teleport will follow. */
    public boolean isCancelled() {
        return cancelled;
    }

    public PlayerRef player() {
        return player;
    }

    public TeleportKind kind() {
        return kind;
    }

    public Position origin() {
        return origin;
    }

    public Destination destination() {
        return destination;
    }

    private PendingTeleport markCancelled() {
        return new PendingTeleport(player, kind, origin, destination, toggles, true);
    }

    /**
     * The result of reconciling an event against a pending warmup: the (possibly cancelled) state and,
     * when a cancel fired, the reason, so the caller raises {@code WarmupCancelled} exactly once.
     *
     * @param state the warmup after reconciliation
     * @param cancelReason the reason a cancel fired, or {@code null} when nothing changed
     */
    public record Outcome(
            PendingTeleport state,
            @org.jspecify.annotations.Nullable WarmupCancelReason cancelReason) {

        public Outcome {
            Objects.requireNonNull(state, "state");
        }

        static Outcome unchanged(PendingTeleport state) {
            return new Outcome(state, null);
        }

        static Outcome cancelled(PendingTeleport state, WarmupCancelReason reason) {
            return new Outcome(state, Objects.requireNonNull(reason, "reason"));
        }

        /** The cancel reason when this reconciliation fired a cancel, else empty. */
        public Optional<WarmupCancelReason> cancel() {
            return Optional.ofNullable(cancelReason);
        }

        /** True when this reconciliation cancelled the warmup. */
        public boolean didCancel() {
            return cancelReason != null;
        }
    }
}
