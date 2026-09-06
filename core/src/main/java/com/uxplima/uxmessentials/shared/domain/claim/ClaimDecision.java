package com.uxplima.uxmessentials.shared.domain.claim;

/**
 * The outcome of a claim policy check. Each denial variant maps to a distinct reason; the calling
 * context maps a denial to its own {@code MessageKey} rather than carrying a message here, so
 * {@code shared} stays free of context-specific enums.
 */
public enum ClaimDecision {

    /** The action is permitted, no claim policy objection. */
    ALLOWED,

    /** The target block is inside another player's claim and the foreign-claim rule is active. */
    DENIED_FOREIGN,

    /** No claim covers the target block and a claim is required to place a home there. */
    DENIED_REQUIRED,

    /** The target block is outside any claim but a foreign claim is too close. */
    DENIED_TOO_CLOSE,

    /** The player lacks access to the claim that covers the target block. */
    DENIED_ACCESS;

    /** Convenience: {@code true} only when the action is allowed. */
    public boolean allowed() {
        return this == ALLOWED;
    }
}
