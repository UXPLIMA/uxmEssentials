package com.uxplima.uxmessentials.security.domain;

/**
 * The outcome of judging one client brand against a {@link ClientPolicy}: whether the player is allowed to stay on
 * this join, and whether the brand is flagged for staff attention. The two flags are independent. A brand can be
 * allowed yet flagged (the {@link ClientIdMode#FLAG} observe mode), allowed and unremarkable, or denied.
 *
 * @param allowed whether the player may join with this brand (a denied brand is kicked on join)
 * @param flagged whether the brand should raise a staff notice (a watched or a blocked brand)
 */
public record ClientVerdict(boolean allowed, boolean flagged) {

    /** The verdict for a brand that raises nothing: the player joins and no staff notice is raised. */
    public static ClientVerdict clear() {
        return new ClientVerdict(true, false);
    }

    /** The verdict for a brand that is allowed but worth a staff notice (the observe/flag mode). */
    public static ClientVerdict flag() {
        return new ClientVerdict(true, true);
    }

    /** The verdict for a brand that is refused: the player is kicked and staff are notified. */
    public static ClientVerdict deny() {
        return new ClientVerdict(false, true);
    }
}
