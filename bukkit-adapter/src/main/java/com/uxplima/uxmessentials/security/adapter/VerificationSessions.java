package com.uxplima.uxmessentials.security.adapter;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.NullMarked;

/**
 * The session-scoped freeze flag of the join-verification flow: who is currently frozen and awaiting verification. It
 * is transient in-memory state owned solely by the security adapter. The freeze listeners read {@link #isPending(UUID)}
 * to decide whether to cancel a frozen player's action. The durable, account-scoped failure counter and lockout window
 * live separately in the shared {@link com.uxplima.uxmessentials.security.application.AttemptLimiter}, so that a relog
 * clears the freeze but never the accumulated attempt budget.
 *
 * <p>Each freeze also carries a token, a number that only ever goes up. It exists for the delayed work a freeze starts
 * (the entry time limit), which fires long after the freeze that scheduled it and must be able to tell "this player is
 * still in the freeze I was started for" from "this player verified, left, rejoined, and is in a new one". Comparing
 * tokens answers that; a bare {@code isPending} check cannot, and would let a stale timer kick someone out of a freeze
 * it was never watching.
 *
 * <p>The map is a {@link ConcurrentHashMap} because the join flow's async worker, the keypad's verify worker, and the
 * event threads all touch it; every mutation is a single atomic operation. On join the player is frozen optimistically
 * before the async enrolment lookup runs, and cleared again once that lookup proves them not-enrolled or on a trusted
 * device, so the default is "frozen until proven safe". Everything is dropped on {@link #clearAll()} at module stop,
 * so a disable leaves no residual freeze.
 */
@NullMarked
public final class VerificationSessions {

    /** A UUID's presence means the player is frozen; the value is the token identifying which freeze. */
    private final Map<UUID, Long> pending = new ConcurrentHashMap<>();

    /** Hands out the tokens. Monotonic for the process lifetime, so no two freezes ever share one. */
    private final AtomicLong tokens = new AtomicLong();

    /**
     * Mark {@code playerId} frozen and awaiting verification, and return the token of this freeze. Re-affirming an
     * optimistic freeze is safe, but it does start a new token: a second {@code begin} is a second freeze, and work
     * queued by the first must not act on it.
     */
    public long begin(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        long token = tokens.incrementAndGet();
        pending.put(playerId, token);
        return token;
    }

    /** Whether {@code playerId} is currently frozen awaiting verification. */
    public boolean isPending(UUID playerId) {
        return pending.containsKey(Objects.requireNonNull(playerId, "playerId"));
    }

    /** Whether {@code playerId} is still frozen in the specific freeze that {@code token} identifies. */
    public boolean isPending(UUID playerId, long token) {
        Long current = pending.get(Objects.requireNonNull(playerId, "playerId"));
        return current != null && current == token;
    }

    /** Clear {@code playerId}'s pending freeze: on a successful verification, a cleared optimistic freeze, or a quit. */
    public void clear(UUID playerId) {
        pending.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    /** Drop every pending freeze, called on module stop so no verification freeze survives a disable. */
    public void clearAll() {
        pending.clear();
    }
}
