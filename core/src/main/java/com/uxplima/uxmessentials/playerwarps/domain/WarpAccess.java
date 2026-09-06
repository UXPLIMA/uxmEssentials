package com.uxplima.uxmessentials.playerwarps.domain;

import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * How a player-warp gates the players who may use it:
 *
 * <ul>
 *   <li>{@link #PUBLIC}: anyone may teleport to it.
 *   <li>{@link #PASSWORD}. A teleport requires the correct password (see the password hashing in a later
 *       task); the owner is exempt.
 *   <li>{@link #WHITELIST}: only players the owner has explicitly added may teleport.
 *   <li>{@link #PRIVATE}: only the owner may teleport.
 * </ul>
 *
 * <p>This axis is orthogonal to {@link WarpStatus}: a warp can be public yet suspended. The persisted token is
 * the constant's {@link #name()} (uppercase); {@link #parse(String)} reads it back case-insensitively.
 */
public enum WarpAccess {
    PUBLIC,
    PASSWORD,
    WHITELIST,
    PRIVATE;

    /**
     * Match a stored or user-supplied token to a constant, ignoring case and surrounding whitespace. Returns
     * an empty result, never throws, for {@code null}, blank, or unrecognised input.
     */
    public static Optional<WarpAccess> parse(@Nullable String token) {
        if (token == null) {
            return Optional.empty();
        }
        String normalised = token.strip().toUpperCase(Locale.ROOT);
        if (normalised.isEmpty()) {
            return Optional.empty();
        }
        for (WarpAccess access : values()) {
            if (access.name().equals(normalised)) {
                return Optional.of(access);
            }
        }
        return Optional.empty();
    }
}
