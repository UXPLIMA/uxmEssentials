package com.uxplima.uxmessentials.playerwarps.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A player-warp's name in its canonical form: lowercase, made only of {@code [a-z0-9_-]}, and between
 * {@value #MIN_LENGTH} and {@value #MAX_LENGTH} characters. Normalising to lowercase keeps lookup
 * case-insensitive: {@code /setpwarp Base} and {@code /pwarp base} address the same warp.
 *
 * <p>This value object enforces only the <em>shape</em> of a name. Whether a name is already taken is not its
 * concern: name uniqueness is enforced at the persistence layer against the {@code player_warps} table. An
 * invalid shape is rejected at construction, so a malformed name can never reach the aggregate or the
 * repository.
 *
 * @param value the canonical lowercase name
 */
public record PlayerWarpName(String value) {

    /** Shortest accepted name length. */
    public static final int MIN_LENGTH = 3;

    /** Longest accepted name length; mirrors the {@code player_warps.name} column width. */
    public static final int MAX_LENGTH = 32;

    private static final Pattern CHARSET = Pattern.compile("[a-z0-9_-]+");

    public PlayerWarpName {
        Objects.requireNonNull(value, "value");
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "player warp name must be " + MIN_LENGTH + ".." + MAX_LENGTH + " characters: " + value);
        }
        if (!CHARSET.matcher(value).matches()) {
            throw new IllegalArgumentException("player warp name must match [a-z0-9_-]: " + value);
        }
    }

    /** Build a name from raw player input, trimming and lower-casing it before validating its shape. */
    public static PlayerWarpName of(String raw) {
        Objects.requireNonNull(raw, "raw");
        return new PlayerWarpName(raw.strip().toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
