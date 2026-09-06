package com.uxplima.uxmessentials.kits.domain;

import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;

/**
 * The modelled failures a kit operation can produce. Each value carries the {@link KitsMessageKey} the
 * command adapter renders, so a use case returns a {@code Result.err(KitError.X)} and the caller never
 * re-derives the message. The error carries it, and the failure reason and its localized text never drift
 * apart.
 */
public enum KitError {

    /** An id no kit exists under: {@code /kit}, {@code /kit del}, {@code /kit editor}, {@code /kit show}. */
    NOT_FOUND(KitsMessageKey.KIT_NOT_FOUND),

    /** {@code /kit create} for an id a kit already exists under. */
    ALREADY_EXISTS(KitsMessageKey.KIT_ALREADY_EXISTS),

    /** No kits are defined at all when one was required ({@code /kit list} renders its own empty notice). */
    NONE_DEFINED(KitsMessageKey.KIT_NONE),

    /** The player lacks the kit's required permission (the per-kit node). */
    NO_PERMISSION(KitsMessageKey.KIT_NO_PERMISSION),

    /** The kit's cooldown has not yet elapsed for the player. */
    ON_COOLDOWN(KitsMessageKey.KIT_ON_COOLDOWN),

    /** A one-time kit the player has already consumed. */
    ALREADY_CLAIMED(KitsMessageKey.KIT_ALREADY_CLAIMED),

    /** The player does not satisfy one or more of the kit's claim requirements (placeholder conditions). */
    REQUIREMENTS_NOT_MET(KitsMessageKey.KIT_REQUIREMENTS_NOT_MET),

    /** The player cannot pay the kit's cost (only reachable when an economy provider is present). */
    CANNOT_AFFORD(KitsMessageKey.KIT_CANNOT_AFFORD),

    /** The kit's availability schedule does not admit a claim at the current time (its rotation window is closed). */
    UNAVAILABLE(KitsMessageKey.KIT_UNAVAILABLE),

    /** The kit's global stock limit has been reached: no copies remain for anyone to claim. */
    OUT_OF_STOCK(KitsMessageKey.KIT_OUT_OF_STOCK),

    /** The claim was cancelled by an external plugin event. */
    CANCELLED(KitsMessageKey.KIT_CLAIM_CANCELLED),

    /** The recipient's inventory could not hold the kit and its {@code on-full} policy is {@code DENY}. */
    INVENTORY_FULL(KitsMessageKey.KIT_INVENTORY_FULL),
    /** Another plugin refused the action through the developer API. Nothing was written. */
    VETOED(SharedMessageKey.COMMON_ACTION_VETOED);

    // Typed as the MessageKey interface rather than this context's own enum: a veto is refused for the same reason
    // in every context, so it renders one shared key rather than twenty near-identical ones.
    private final MessageKey messageKey;

    KitError(MessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public MessageKey messageKey() {
        return messageKey;
    }
}
