package com.uxplima.uxmessentials.itemworld.application;

import java.util.Objects;

import com.uxplima.uxmessentials.itemworld.domain.EnchantSpec;
import com.uxplima.uxmessentials.itemworld.domain.ItemWorldError;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * The {@code /enchant <enchant> [level]} clamp policy: normalise the enchantment id and clamp the requested
 * level to the configured ceiling before the adapter applies it to the held item. The clamp is the one real
 * rule for {@code /enchant} (it prevents an over-vanilla level being handed to the registry verbatim) so it
 * lives here and is unit-testable in {@code :core}.
 *
 * <p>The ceiling comes from {@link ItemworldConfig#maxEnchantLevel()}. A blank id is rejected with
 * {@link ItemWorldError#UNKNOWN_ENCHANT} (an id that is well-formed here but unknown to the registry is
 * rejected the same way at the adapter boundary); a level above the ceiling clamps down and the resulting
 * {@link EnchantSpec#clamped()} flag lets the adapter tell the actor the level was reduced.
 */
public final class EnchantPolicy {

    private final ItemworldConfig config;

    public EnchantPolicy(ItemworldConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Validate and clamp an enchant request; the level defaults to 1 when the actor omits it. */
    public Result<EnchantSpec, ItemWorldError> validate(String rawEnchant, int requestedLevel) {
        return EnchantSpec.of(rawEnchant, requestedLevel, config.maxEnchantLevel())
                .<Result<EnchantSpec, ItemWorldError>>map(Result::ok)
                .orElseGet(() -> Result.err(ItemWorldError.UNKNOWN_ENCHANT));
    }
}
