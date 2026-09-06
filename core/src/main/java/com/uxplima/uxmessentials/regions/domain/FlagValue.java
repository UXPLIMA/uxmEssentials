package com.uxplima.uxmessentials.regions.domain;

import java.util.Objects;

/**
 * One region flag as a plain, rendered name/value pair. The domain view of a WorldGuard flag decoupled from its
 * {@code com.sk89q} {@code Flag} type. The adapter maps a live flag entry to this ({@code pvp = DENY},
 * {@code greeting = "Welcome"}), so the application and the (Phase 2) flag editor reason over strings rather than
 * WorldGuard's typed flag registry.
 *
 * <p>{@link #value()} is the flag's current value stringified, or an empty string when the flag is set but carries
 * no printable value. Pure Java: no Bukkit, Paper, Kyori, or WorldGuard.
 *
 * @param name the flag's registered name (e.g. {@code pvp}, {@code greeting})
 * @param value the flag's current value rendered as a string, or empty when it has none
 */
public record FlagValue(String name, String value) {

    public FlagValue {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (name.isBlank()) {
            throw new IllegalArgumentException("flag name must not be blank");
        }
    }

    /**
     * A flag value carrying {@code state} for {@code name}: the state's {@link FlagState#token() token} becomes the
     * {@link #value()}, so cycling a flag in the editor round-trips through the {@code RegionService} port as a plain
     * name/value pair. An {@link FlagState#UNSET} state carries the empty value (the "clear this flag" request).
     */
    public static FlagValue of(String name, FlagState state) {
        Objects.requireNonNull(state, "state");
        return new FlagValue(name, state.token());
    }

    /**
     * This value read as a {@link FlagState}, strictly: {@code "ALLOW"} / {@code "DENY"} / blank map to the matching
     * state and anything else is rejected. Call this only when the flag is known to be a state flag (the editor's
     * config-declared list); a free-text flag's value is not a state and would raise.
     *
     * @throws IllegalArgumentException when the value names no known state
     */
    public FlagState state() {
        return FlagState.parse(value);
    }
}
