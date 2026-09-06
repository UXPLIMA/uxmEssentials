package com.uxplima.uxmessentials.kits.domain;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * What a kit does when the recipient's inventory cannot hold everything it grants. {@link #DROP} keeps the
 * long-standing behaviour. The items that did not fit are dropped at the player's feet; {@link #DENY} refuses
 * the claim outright when there is not enough free space, granting nothing and leaving the cooldown, the
 * one-time stamp, and the stock untouched so the player may retry once they have made room.
 *
 * <p>The token an operator writes is the constant's lowercase name ({@code drop}/{@code deny}) under a kit's
 * {@code on-full} key; {@link #parse(String)} maps it back, defaulting to {@link #DROP} for a blank or
 * unrecognised value so a typo never turns a forgiving kit into a refusing one.
 */
public enum KitFullPolicy {

    /** Drop the overflow at the player's feet, the default, the historical kit behaviour. */
    DROP,

    /** Refuse the claim when the inventory cannot hold everything, granting nothing. */
    DENY;

    /** The token this policy is written as under a kit's {@code on-full} key (its lowercase name). */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parse an operator-written {@code on-full} value into its policy, tolerating surrounding spaces and case,
     * and defaulting to {@link #DROP} for a {@code null}, blank, or unrecognised token so the kit stays
     * forgiving unless an operator deliberately opts into {@link #DENY}.
     */
    public static KitFullPolicy parse(@Nullable String raw) {
        if (raw == null) {
            return DROP;
        }
        String normalized = raw.strip().toUpperCase(Locale.ROOT);
        for (KitFullPolicy policy : values()) {
            if (policy.name().equals(normalized)) {
                return policy;
            }
        }
        return DROP;
    }
}
