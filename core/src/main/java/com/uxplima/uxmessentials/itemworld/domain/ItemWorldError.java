package com.uxplima.uxmessentials.itemworld.domain;

import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;

/**
 * The modelled, expected failures of an itemworld action, the {@code E} arm of a {@code Result} a use case
 * or a validator returns instead of throwing. Each value carries the {@link ItemworldMessageKey} the adapter
 * renders to the actor, so the failure reason and its localized text never drift apart (the same pattern the
 * moderation context's {@code ModerationError} uses).
 *
 * <p>These are first-class outcomes of input validation at the adapter boundary (an unparsable item id, an
 * amount over the configured cap, an unknown enchant, a bad time/weather token, an unknown mob, an empty
 * hand) or of a disabled command/sub-group. A genuinely exceptional fault (a null world handle, a corrupt
 * registry entry) still throws.
 */
public enum ItemWorldError {

    /** The item id did not resolve to a known material/registry entry. */
    UNKNOWN_ITEM(ItemworldMessageKey.UNKNOWN_ITEM),

    /** The requested amount was not a positive integer within the configured cap. */
    AMOUNT_OUT_OF_RANGE(ItemworldMessageKey.AMOUNT_OUT_OF_RANGE),

    /** The enchantment id did not resolve to a known enchantment. */
    UNKNOWN_ENCHANT(ItemworldMessageKey.ENCHANT_UNKNOWN),

    /** The time token did not parse to a tick value or a known keyword. */
    BAD_TIME(ItemworldMessageKey.BAD_TIME),

    /** The weather token did not parse to a known weather kind. */
    BAD_WEATHER(ItemworldMessageKey.BAD_WEATHER),

    /** The mob type did not resolve to a spawnable entity. */
    UNKNOWN_MOB(ItemworldMessageKey.SPAWNMOB_UNKNOWN),

    /** The named/uuid target did not resolve to a known player. */
    UNKNOWN_TARGET(ItemworldMessageKey.UNKNOWN_TARGET),

    /** The actor's hand was empty for a held-item verb ({@code /more}, {@code /hat}, {@code /enchant}, …). */
    NO_ITEM_IN_HAND(ItemworldMessageKey.NO_ITEM_IN_HAND),

    /** The command (or its sub-feature group) is disabled in {@code itemworld.conf}. */
    COMMAND_DISABLED(ItemworldMessageKey.COMMAND_DISABLED);

    private final ItemworldMessageKey messageKey;

    ItemWorldError(ItemworldMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public ItemworldMessageKey messageKey() {
        return messageKey;
    }
}
