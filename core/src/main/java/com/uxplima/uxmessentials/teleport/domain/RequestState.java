package com.uxplima.uxmessentials.teleport.domain;

/**
 * The lifecycle phase of a {@link TeleportRequest}, with the legal transitions encoded as guards.
 *
 * <p>A request is born {@link Phase#PENDING}; the target may {@code accept} it (→ {@link Phase#ACCEPTED})
 * or {@code deny} it (→ {@link Phase#DENIED}); the requester may {@code cancel} it
 * (→ {@link Phase#CANCELLED}); the TTL may {@code expire} it (→ {@link Phase#EXPIRED}); and an accepted
 * request {@code completes} on arrival (→ {@link Phase#COMPLETED}). Every terminal phase is final, a
 * resolved request never moves again, which is what lets {@link CooldownStartPhase#burnsOn} reason about
 * exactly one burning transition.
 */
public final class RequestState {

    /** The discrete phases a teleport request passes through. */
    public enum Phase {
        PENDING,
        ACCEPTED,
        COMPLETED,
        DENIED,
        CANCELLED,
        EXPIRED
    }

    private final Phase phase;

    private RequestState(Phase phase) {
        this.phase = phase;
    }

    /** A freshly issued, unresolved request. */
    public static RequestState pending() {
        return new RequestState(Phase.PENDING);
    }

    /** The current phase. */
    public Phase phase() {
        return phase;
    }

    /** True while the request can still be accepted, denied, cancelled, or expired. */
    public boolean isPending() {
        return phase == Phase.PENDING;
    }

    /** True once the request reached any phase from which it can no longer transition. */
    public boolean isTerminal() {
        return phase != Phase.PENDING && phase != Phase.ACCEPTED;
    }

    /** Move a pending request to {@link Phase#ACCEPTED}. */
    public RequestState accept() {
        return transitionFromPending(Phase.ACCEPTED);
    }

    /** Move a pending request to {@link Phase#DENIED}. */
    public RequestState deny() {
        return transitionFromPending(Phase.DENIED);
    }

    /** Move a pending request to {@link Phase#CANCELLED}. */
    public RequestState cancel() {
        return transitionFromPending(Phase.CANCELLED);
    }

    /** Move a pending request to {@link Phase#EXPIRED}. */
    public RequestState expire() {
        return transitionFromPending(Phase.EXPIRED);
    }

    /** Move an accepted request to {@link Phase#COMPLETED} once the player has arrived. */
    public RequestState complete() {
        if (phase != Phase.ACCEPTED) {
            throw new IllegalStateException("only an accepted request completes, was " + phase);
        }
        return new RequestState(Phase.COMPLETED);
    }

    private RequestState transitionFromPending(Phase next) {
        if (phase != Phase.PENDING) {
            throw new IllegalStateException("expected PENDING to reach " + next + ", was " + phase);
        }
        return new RequestState(next);
    }
}
