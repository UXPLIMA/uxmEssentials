package com.uxplima.uxmessentials.teleport.domain;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * One captured {@code /back} return point: where the player was, what caused the capture, and when.
 *
 * <p>A player has at most one current {@code BackLocation}. Each capture supersedes the last, so
 * {@code /back} always returns to the most recent of (the pre-teleport snapshot, the death point). The
 * capture is in-flight per-player state the teleport module re-arms on relog; it is never PDC-stamped
 * by the domain.
 *
 * @param position the position {@code /back} returns to
 * @param cause whether this was a pre-teleport snapshot or a death point
 * @param capturedAt when the capture happened, for tie-breaking and audit
 */
public record BackLocation(Position position, BackCause cause, Instant capturedAt) {

    public BackLocation {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(capturedAt, "capturedAt");
    }

    /** A pre-teleport capture: the location to leave, taken just before a hop. */
    public static BackLocation beforeTeleport(Position position, Instant at) {
        return new BackLocation(position, BackCause.TELEPORT, at);
    }

    /** A death capture: only returnable when the player holds the death-back permission. */
    public static BackLocation atDeath(Position position, Instant at) {
        return new BackLocation(position, BackCause.DEATH, at);
    }

    /** True when returning here requires the {@code uxmessentials.back.ondeath} permission. */
    public boolean requiresDeathBack() {
        return cause == BackCause.DEATH;
    }

    /**
     * The pure return decision: a death capture is only returnable when {@code deathBackAllowed}; a
     * teleport capture is always returnable. The use case supplies the permission outcome.
     */
    public Result<Position, TeleportError> resolveReturn(boolean deathBackAllowed) {
        if (requiresDeathBack() && !deathBackAllowed) {
            return Result.err(TeleportError.BACK_ON_DEATH_DENIED);
        }
        return Result.ok(position);
    }

    /** Convenience for the always-allowed teleport-capture return. */
    public Result<Unit, TeleportError> assertReturnable(boolean deathBackAllowed) {
        return resolveReturn(deathBackAllowed).map(p -> Unit.INSTANCE);
    }
}
