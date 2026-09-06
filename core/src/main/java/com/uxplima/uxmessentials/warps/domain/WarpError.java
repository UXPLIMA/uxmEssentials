package com.uxplima.uxmessentials.warps.domain;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;

/**
 * The modelled failures a warp operation can produce. Each value carries the {@link WarpsMessageKey} the
 * command adapter renders, so a use case returns a {@code Result.err(WarpError.X)} and the caller never
 * re-derives the message. The error carries it, and the failure reason and its localized text never
 * drift apart.
 */
public enum WarpError {

    /** A name no warp exists under: {@code /warp}, {@code /delwarp}, {@code /movewarp}, {@code /warpinfo}. */
    NOT_FOUND(WarpsMessageKey.WARP_NOT_FOUND),

    /** {@code /setwarp} for a name a warp already exists under, when a re-anchor was not intended. */
    NAME_TAKEN(WarpsMessageKey.WARP_NAME_TAKEN),

    /** No warps exist at all when one was required ({@code /warps} renders its own empty notice). */
    NONE_SET(WarpsMessageKey.WARP_NONE),

    /** The player lacks the warp's required permission (the per-warp node or its extra gate). */
    NO_PERMISSION(WarpsMessageKey.WARP_NO_PERMISSION),

    /** The player cannot pay the warp's cost (only reachable when an economy provider is present). */
    CANNOT_AFFORD(WarpsMessageKey.WARP_CANNOT_AFFORD),

    /** The warp destination is physically unsafe (lava, void, suffocation). */
    UNSAFE_LOCATION(WarpsMessageKey.WARP_UNSAFE),

    /** The warp is locked. */
    LOCKED(WarpsMessageKey.WARP_LOCKED),

    /** The player entered the wrong password. */
    WRONG_PASSWORD(WarpsMessageKey.WARP_WRONG_PASSWORD),

    /** The warp cannot be created in this world because it is blacklisted. */
    WORLD_BLACKLISTED(WarpsMessageKey.WARP_WORLD_BLACKLISTED),
    /** Another plugin refused the action through the developer API. Nothing was written. */
    VETOED(SharedMessageKey.COMMON_ACTION_VETOED);

    // Typed as the MessageKey interface rather than this context's own enum: a veto is refused for the same reason
    // in every context, so it renders one shared key rather than twenty near-identical ones.
    private final MessageKey messageKey;

    WarpError(MessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public MessageKey messageKey() {
        return messageKey;
    }
}
