package com.uxplima.uxmessentials.security.domain;

/**
 * The pure decision behind the join-verification lockout: given how many times a player has failed to prove their
 * second factor this session, decide whether they may try again or have exhausted their attempts and must be kicked.
 * It is a value object so the rule is unit-testable without a player, a keypad, or a clock. The keypad adapter feeds
 * it the running failure count and acts on the verdict.
 *
 * <p>The count passed to {@link #evaluate(int)} is the total <em>after</em> the latest failure, so a policy of
 * {@code maxAttempts = 3} returns {@link AttemptOutcome#RETRY} for the first two failures and
 * {@link AttemptOutcome#LOCKED_OUT} on the third. {@link #remaining(int)} reports how many attempts are left, never
 * negative, so a prompt can tell the player how many tries remain.
 *
 * @param maxAttempts the number of failures that triggers a lockout (at least one)
 */
public record LockoutPolicy(int maxAttempts) {

    public LockoutPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1: " + maxAttempts);
        }
    }

    /** Whether the player may try again or has run out of attempts, given the failure count so far this session. */
    public AttemptOutcome evaluate(int failuresSoFar) {
        if (failuresSoFar < 0) {
            throw new IllegalArgumentException("failuresSoFar must not be negative: " + failuresSoFar);
        }
        return failuresSoFar >= maxAttempts ? AttemptOutcome.LOCKED_OUT : AttemptOutcome.RETRY;
    }

    /** How many attempts remain after {@code failuresSoFar} failures, clamped at zero, never negative. */
    public int remaining(int failuresSoFar) {
        return Math.max(0, maxAttempts - Math.max(0, failuresSoFar));
    }

    /** The verdict {@link LockoutPolicy#evaluate(int)} returns: try again, or the player is locked out and kicked. */
    public enum AttemptOutcome {

        /** The player still has attempts left and should be re-prompted. */
        RETRY,

        /** The player has exhausted their attempts and must be kicked and put on the lockout cooldown. */
        LOCKED_OUT
    }
}
