package com.uxplima.uxmessentials.moderation.domain;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * How far a UUID ban reaches across the banned player's known addresses (the {@code address-strictness}
 * moderation setting). {@link #NORMAL} is the default and the privacy-conservative behaviour, a {@code /ban}
 * bans the one account and nothing else. {@link #STRICT} also IP-bans every address the target is known to
 * have connected from, so a banned cheater cannot simply log back in on a fresh account from the same
 * connection; the collateral is that anyone who legitimately shares that address (a household, a shared
 * network) is caught too, which is why STRICT is opt-in and never IP-bans an exempt target.
 */
public enum AddressStrictness {
    NORMAL,
    STRICT;

    /**
     * Parse a configured strictness name, falling back to {@link #NORMAL} for a blank or unrecognised value so
     * a typo never silently widens a ban's reach. Case-insensitive.
     */
    public static AddressStrictness parse(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return NORMAL;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return NORMAL;
        }
    }

    /** True when a UUID ban should fan out to the target's known IPs. */
    public boolean fansOutToIps() {
        return this == STRICT;
    }
}
