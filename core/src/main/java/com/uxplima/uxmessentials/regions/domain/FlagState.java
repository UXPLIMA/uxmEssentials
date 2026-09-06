package com.uxplima.uxmessentials.regions.domain;

import java.util.Objects;

/**
 * The tri-state a WorldGuard state flag can carry, decoupled from {@code com.sk89q}'s {@code StateFlag.State}: a flag
 * is either explicitly {@link #ALLOW}ed, explicitly {@link #DENY}ed, or {@link #UNSET} (no value, so the region falls
 * back to the default). The flag editor cycles a flag through these three, so the domain owns the cycle order and the
 * string tokens the {@code RegionService} port speaks, and the adapter never has to reason about WorldGuard's typed
 * flag registry to know what a click means.
 *
 * <p>The declared order is the cycle {@link #next} advances through, {@code UNSET → ALLOW → DENY → UNSET}, so a
 * fresh flag first enables, then denies, then clears. {@link #token} is the wire form carried inside a
 * {@link FlagValue} ({@code "ALLOW"}, {@code "DENY"}, or the empty string for a cleared flag). Pure Java: no Bukkit,
 * Paper, Kyori, or WorldGuard.
 */
public enum FlagState {

    /** No value set: the flag falls back to WorldGuard's default. Carried as the empty token. */
    UNSET(""),

    /** The flag is explicitly allowed. */
    ALLOW("ALLOW"),

    /** The flag is explicitly denied. */
    DENY("DENY");

    private final String token;

    FlagState(String token) {
        this.token = token;
    }

    /** The wire form a {@link FlagValue} carries for this state: {@code "ALLOW"}, {@code "DENY"}, or empty for unset. */
    public String token() {
        return token;
    }

    /** The next state in the editor cycle: {@code UNSET → ALLOW → DENY → UNSET}. */
    public FlagState next() {
        return switch (this) {
            case UNSET -> ALLOW;
            case ALLOW -> DENY;
            case DENY -> UNSET;
        };
    }

    /**
     * Strictly parse a rendered flag value into a state: a blank value (or {@code "unset"}) is {@link #UNSET}, and
     * {@code "allow"} / {@code "deny"} (any case) are the matching states. Any other token is rejected: this is the
     * validate boundary that keeps an out-of-range value from ever reaching WorldGuard.
     *
     * @throws IllegalArgumentException when {@code raw} names no known state
     */
    public static FlagState parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String token = raw.strip();
        if (token.isEmpty() || token.equalsIgnoreCase(UNSET.name())) {
            return UNSET;
        }
        if (token.equalsIgnoreCase(ALLOW.token())) {
            return ALLOW;
        }
        if (token.equalsIgnoreCase(DENY.token())) {
            return DENY;
        }
        throw new IllegalArgumentException("not a region flag state: " + raw);
    }

    /**
     * Leniently read a rendered flag value into a state, treating anything not {@code ALLOW} / {@code DENY} as
     * {@link #UNSET}. Used when reflecting the <em>current</em> value of a live flag onto the editor, where a flag
     * that happens to carry a non-state value (or none) should simply read as unset rather than raise.
     */
    public static FlagState of(String raw) {
        Objects.requireNonNull(raw, "raw");
        try {
            return parse(raw);
        } catch (IllegalArgumentException notAState) {
            return UNSET;
        }
    }
}
