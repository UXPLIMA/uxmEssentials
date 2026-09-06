package com.uxplima.uxmessentials.playerwarps.domain;

/**
 * A single management action a {@link WarpRole} may or may not perform on a warp. Every management use case gates
 * on one of these through {@code WarpRole#can(WarpCapability)}, so the role-to-action policy lives in exactly one
 * place, the capability matrix on {@link WarpRole}, rather than being re-derived (and drifting) at each call
 * site. The owner holds every capability; delegates hold a narrowing subset by role.
 *
 * <p>The axis is intentionally finer-grained than the roles: splitting metadata, access, and price into separate
 * capabilities lets a {@code MANAGER} touch presentation without ever reaching the money or the lifecycle, which
 * is the whole reason the role tier exists.
 */
public enum WarpCapability {

    /** Edit the presentation facets: display name, description, icon, category. */
    EDIT_METADATA,

    /** Change the access axis (public / password / whitelist / private) and set or clear the password. */
    EDIT_ACCESS,

    /** Change the entry price and its currency. */
    EDIT_PRICE,

    /** Re-anchor the warp at a new position. */
    MOVE,

    /** Change the warp's globally-unique name. */
    RENAME,

    /** Hand ownership of the warp to another player. */
    TRANSFER,

    /** Archive, restore, or hard-delete the warp. */
    DELETE,

    /** Add or remove players on the warp's whitelist. */
    MANAGE_WHITELIST,

    /** Ban or unban players from the warp. */
    MANAGE_BANS,

    /** Grant or revoke co-owner / manager membership on the warp. */
    MANAGE_MEMBERS,

    /** Withdraw the warp's accrued earnings to the owner's balance. */
    WITHDRAW,

    /** Buy a paid, time-limited sponsored browse slot for the warp: spends the owner's own money, owner-only. */
    SPONSOR
}
