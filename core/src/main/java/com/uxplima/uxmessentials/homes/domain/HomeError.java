package com.uxplima.uxmessentials.homes.domain;

import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;

/**
 * The modelled failures a home operation can produce. Each value carries the {@link HomesMessageKey} the
 * command adapter renders, so a use case returns a {@code Result.err(HomeError.X)} and the caller never
 * re-derives the message. The error carries it, and the failure reason and its localized text never
 * drift apart.
 */
public enum HomeError {

    /** {@code /sethome} into a new slot past the owner's resolved {@code uxmessentials.home.limit.<n>}. */
    LIMIT_REACHED(HomesMessageKey.HOME_LIMIT_REACHED),

    /** A slot the owner has no home in: teleport, relocate, relabel, re-icon, or delete of an empty slot. */
    NOT_FOUND(HomesMessageKey.HOME_NOT_FOUND),

    /** {@code /sethome} into a slot the owner already has a home in. */
    SLOT_TAKEN(HomesMessageKey.HOME_SLOT_TAKEN),

    /** A slot index at or above the owner's resolved maximum-slot count. */
    SLOT_OUT_OF_RANGE(HomesMessageKey.HOME_SLOT_OUT_OF_RANGE),

    /** The target position is in a world where homes are administratively disabled. */
    WORLD_DISABLED(HomesMessageKey.HOME_WORLD_DISABLED),

    /** The target position is flagged as unsafe to place a home (e.g. in the air, under lava). */
    UNSAFE_LOCATION(HomesMessageKey.HOME_UNSAFE_LOCATION),

    /** The player's balance was insufficient to cover the per-action economy cost. */
    CANNOT_AFFORD(HomesMessageKey.HOME_CANNOT_AFFORD),

    /** A visit to another owner's home that is neither public nor extended an invite to the actor. */
    NOT_ACCESSIBLE(HomesMessageKey.HOME_NOT_ACCESSIBLE),

    /** Another plugin refused the action through the developer API. Nothing was written. */
    VETOED(SharedMessageKey.COMMON_ACTION_VETOED);

    // Typed as the MessageKey interface rather than the context's own enum: a veto is refused for the same reason
    // in every context, so it renders one shared key rather than twenty near-identical ones.
    private final MessageKey messageKey;

    HomeError(MessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public MessageKey messageKey() {
        return messageKey;
    }
}
