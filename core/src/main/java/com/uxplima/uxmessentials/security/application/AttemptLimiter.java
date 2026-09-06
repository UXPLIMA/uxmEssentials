package com.uxplima.uxmessentials.security.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.security.domain.LockoutPolicy;

/**
 * The durable, account-scoped brute-force limiter shared by every verification surface, the join freeze, op-command
 * re-authentication, and {@code /2fa disable}. It counts consecutive failed proofs per account (keyed by UUID, never
 * per session), so the running budget survives a disconnect and rejoin: an attacker cannot reset their attempts by
 * relogging before the lockout-triggering guess. When the count reaches the configured limit the account is put on a
 * timed lockout during which no surface accepts an attempt, and a successful proof (or an expired lockout) starts a
 * fresh budget.
 *
 * <p>State lives in two {@link ConcurrentHashMap}s keyed by player UUID; every mutation is a single atomic map
 * operation. It is deliberately in-memory, matching the module's existing in-memory lockout instant, so it is durable
 * across a reconnect within the running server and reset wholesale on module stop via {@link #clearAll()}. The pure
 * retry/lock decision is delegated to {@link LockoutPolicy}; this class only owns the running counts and the timed
 * windows so the three adapters and the {@code DisableTwoFactor} use case all consult one shared limiter.
 */
public final class AttemptLimiter {

    private final LockoutPolicy policy;
    private final Duration lockout;

    /** UUID → consecutive failed proofs since the last success or lockout; absence means zero. */
    private final ConcurrentHashMap<UUID, Integer> failures = new ConcurrentHashMap<>();

    /** UUID → the instant an active lockout expires; presence within the window blocks every surface. */
    private final ConcurrentHashMap<UUID, Instant> lockedUntil = new ConcurrentHashMap<>();

    public AttemptLimiter(LockoutPolicy policy, Duration lockout) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.lockout = Objects.requireNonNull(lockout, "lockout");
        if (lockout.isNegative()) {
            throw new IllegalArgumentException("lockout must not be negative: " + lockout);
        }
    }

    /** Whether {@code playerId} is currently locked out as of {@code now}; an expired lockout is cleared and reset. */
    public boolean isLockedOut(UUID playerId, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(now, "now");
        Instant until = lockedUntil.get(playerId);
        if (until == null) {
            return false;
        }
        if (!now.isBefore(until)) {
            // The cooldown lapsed: drop it and the accumulated failures so the next join starts a fresh budget.
            lockedUntil.remove(playerId, until);
            failures.remove(playerId);
            return false;
        }
        return true;
    }

    /** Count a failed proof against {@code playerId} and report whether it tipped the account into a lockout. */
    public Outcome recordFailure(UUID playerId, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(now, "now");
        int count = failures.merge(playerId, 1, Integer::sum);
        if (policy.evaluate(count) == LockoutPolicy.AttemptOutcome.LOCKED_OUT) {
            // The budget rolls into the timed lockout; clear the counter so the window (not the count) governs
            // re-entry.
            lockedUntil.put(playerId, now.plus(lockout));
            failures.remove(playerId);
            return Outcome.locked();
        }
        return Outcome.retry(policy.remaining(count));
    }

    /** Forget {@code playerId}'s accumulated failures after a successful proof, so the next lapse starts from zero. */
    public void recordSuccess(UUID playerId) {
        failures.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    /**
     * Lift an active lockout and the failure budget behind it for one account.
     *
     * <p>What staff do for a player who locked themselves out. A lockout the operator chose to write to the ban
     * list is a ban as well, and lifting the window here does not lift that.
     *
     * @return whether a lockout was actually lifted, so a caller can tell that from a no-op
     */
    public boolean clearLockout(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        failures.remove(playerId);
        return lockedUntil.remove(playerId) != null;
    }

    /** Drop every counter and lockout, called on module stop so no limiter state survives a disable. */
    public void clearAll() {
        failures.clear();
        lockedUntil.clear();
    }

    /** The result of {@link #recordFailure}: either locked out now, or still allowed with the attempts remaining. */
    public record Outcome(boolean lockedOut, int remaining) {

        static Outcome locked() {
            return new Outcome(true, 0);
        }

        static Outcome retry(int remaining) {
            return new Outcome(false, remaining);
        }
    }
}
