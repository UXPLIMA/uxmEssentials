package com.uxplima.uxmessentials.playerwarps.domain;

import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;

/**
 * The modelled failures a player-warp operation can produce. Each value carries the
 * {@link PlayerwarpsMessageKey} the command adapter renders, so a use case returns a
 * {@code Result.err(PlayerWarpError.X)} and the caller never re-derives the message. The error carries it,
 * and the failure reason and its localized text never drift apart.
 */
public enum PlayerWarpError {

    /** A name no warp exists under for the owner: {@code /pwarp}, {@code /pwarp del}, the visibility toggles. */
    NOT_FOUND(PlayerwarpsMessageKey.PWARP_NOT_FOUND),

    /** {@code /setpwarp} for a name the owner already has, when a re-anchor was not intended. */
    NAME_TAKEN(PlayerwarpsMessageKey.PWARP_NAME_TAKEN),

    /** {@code /setpwarp} when the owner is already at their resolved warp limit. */
    LIMIT_REACHED(PlayerwarpsMessageKey.PWARP_LIMIT_REACHED),

    /** The owner has no player-warps at all when one was required. */
    NONE_SET(PlayerwarpsMessageKey.PWARP_NONE),

    /** A non-owner tried to use a warp that is still private. */
    NOT_PUBLIC(PlayerwarpsMessageKey.PWARP_NOT_PUBLIC),

    /** The warp is not {@link com.uxplima.uxmessentials.playerwarps.domain.WarpStatus#ACTIVE active}: it is
     * suspended or archived, so nobody (not even the owner) may use it until its status is settled. */
    SUSPENDED(PlayerwarpsMessageKey.PWARP_SUSPENDED),

    /** The actor is banned from this warp and holds no ban bypass. */
    BANNED(PlayerwarpsMessageKey.PWARP_BANNED),

    /** A non-member tried to use a whitelist-access warp they are not on the whitelist for. */
    NOT_WHITELISTED(PlayerwarpsMessageKey.PWARP_NOT_WHITELISTED),

    /** Too many wrong password attempts in a row: the per-warp attempt cooldown is still cooling down. */
    RATE_LIMITED(PlayerwarpsMessageKey.PWARP_RATE_LIMITED),

    /** The entry charge did not complete (the payer could not pay, or the accrual failed), so no visit occurred. */
    CANNOT_AFFORD(PlayerwarpsMessageKey.PWARP_CANNOT_AFFORD),

    /** The warp destination is physically unsafe (lava, void, suffocation). */
    UNSAFE_LOCATION(PlayerwarpsMessageKey.PWARP_UNSAFE),

    /** The warp is locked. */
    LOCKED(PlayerwarpsMessageKey.PWARP_LOCKED),

    /** The player entered the wrong password. */
    WRONG_PASSWORD(PlayerwarpsMessageKey.PWARP_WRONG_PASSWORD),

    /** The warp cannot be created in this world because it is blacklisted. */
    WORLD_BLACKLISTED(PlayerwarpsMessageKey.PWARP_WORLD_BLACKLISTED),

    /** A rating outside the 1..5 star range: {@code /pwarp rate} rejects it before any vote is recorded. */
    RATING_INVALID(PlayerwarpsMessageKey.PWARP_RATING_INVALID),

    /** The owner tried to rate their own warp: self-rating is barred so it cannot cheaply inflate the score. */
    CANNOT_RATE_OWN(PlayerwarpsMessageKey.PWARP_CANNOT_RATE_OWN),

    /** The actor holds no role, or too low a role, to perform this management action on the warp. */
    NO_PERMISSION(PlayerwarpsMessageKey.PWARP_NO_PERMISSION),

    /** A price currency change was refused because the warp's earnings bank is not empty, withdraw it first. */
    CURRENCY_LOCKED(PlayerwarpsMessageKey.PWARP_CURRENCY_LOCKED),

    /** A member grant named {@link com.uxplima.uxmessentials.playerwarps.domain.WarpRole#OWNER}. A warp keeps its
     * single owner, so only co-owner and manager may be granted. */
    INVALID_ROLE(PlayerwarpsMessageKey.PWARP_INVALID_ROLE),

    /** A member or ban verb aimed at the warp's own owner: the owner is neither a member nor bannable on their warp. */
    CANNOT_TARGET_OWNER(PlayerwarpsMessageKey.PWARP_CANNOT_TARGET_OWNER),

    /** A withdraw could not pay the accrued earnings to the owner (the economy provider faulted). */
    WITHDRAW_FAILED(PlayerwarpsMessageKey.PWARP_WITHDRAW_FAILED),

    /** The chosen name collides with a {@code /pwarp} verb literal, so it would be unreachable as a warp name. */
    RESERVED_NAME(PlayerwarpsMessageKey.PWARP_RESERVED_NAME),

    /** A transfer was refused because the warp is currently sponsored: an active sponsorship locks the owner. */
    SPONSORED_LOCKED(PlayerwarpsMessageKey.PWARP_SPONSORED_LOCKED),

    /** {@code /pwarp sponsor} refused because the warp is still within its post-expiry sponsor cooldown. */
    SPONSOR_COOLDOWN(PlayerwarpsMessageKey.PWARP_SPONSOR_COOLDOWN),

    /** {@code /pwarp sponsor} refused because the owner already holds the maximum concurrent sponsorships. */
    SPONSOR_LIMIT(PlayerwarpsMessageKey.PWARP_SPONSOR_LIMIT),

    /** {@code /pwarp sponsor} refused because every sponsor slot is currently taken. */
    SPONSOR_FULL(PlayerwarpsMessageKey.PWARP_SPONSOR_FULL),
    /** Another plugin refused the action through the developer API. Nothing was written. */
    VETOED(SharedMessageKey.COMMON_ACTION_VETOED);

    // Typed as the MessageKey interface rather than this context's own enum: a veto is refused for the same reason
    // in every context, so it renders one shared key rather than twenty near-identical ones.
    private final MessageKey messageKey;

    PlayerWarpError(MessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public MessageKey messageKey() {
        return messageKey;
    }
}
