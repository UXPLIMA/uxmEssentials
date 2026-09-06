package com.uxplima.uxmessentials.playerstate.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * The game modes {@code /gamemode} (and the {@code /gmc /gms /gma /gmsp} aliases) switch between, kept as a
 * domain enum so the application layer never imports Bukkit's {@code GameMode}. The adapter maps each constant
 * to the Bukkit value at the boundary; the canonical alias and the numeric id mirror the vanilla command so
 * {@code /gamemode 1}, {@code /gamemode creative}, and {@code /gmc} all resolve to {@link #CREATIVE}.
 */
public enum GameModeRef {
    SURVIVAL(0, "survival", "s"),
    CREATIVE(1, "creative", "c"),
    ADVENTURE(2, "adventure", "a"),
    SPECTATOR(3, "spectator", "sp");

    private final int id;
    private final String canonical;
    private final String shortAlias;

    GameModeRef(int id, String canonical, String shortAlias) {
        this.id = id;
        this.canonical = canonical;
        this.shortAlias = shortAlias;
    }

    /** The vanilla numeric id (0 survival, 1 creative, 2 adventure, 3 spectator). */
    public int id() {
        return id;
    }

    /** The canonical lowercase name ({@code creative}). */
    public String canonical() {
        return canonical;
    }

    /** The short alias the command also accepts ({@code c} for creative), mirroring the vanilla shortcuts. */
    public String shortAlias() {
        return shortAlias;
    }

    /**
     * Whether this mode lets the player fly: creative and spectator do, survival and adventure do not. The
     * reconciler uses this to lift a player into the air the moment they enter such a mode rather than leaving
     * them to double-jump for it.
     */
    public boolean flies() {
        return this == CREATIVE || this == SPECTATOR;
    }

    /**
     * Resolve a mode from raw input (the full name, its short alias, or the numeric id) case-insensitively.
     * Returns empty when nothing matches, so the command can reject an unknown mode with a localized message.
     */
    public static Optional<GameModeRef> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String token = raw.strip().toLowerCase(Locale.ROOT);
        for (GameModeRef mode : values()) {
            if (mode.canonical.equals(token)
                    || mode.shortAlias.equals(token)
                    || Integer.toString(mode.id).equals(token)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
