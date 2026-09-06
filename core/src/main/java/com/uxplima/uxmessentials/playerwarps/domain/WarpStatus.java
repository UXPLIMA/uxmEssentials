package com.uxplima.uxmessentials.playerwarps.domain;

import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * The lifecycle state of a player-warp:
 *
 * <ul>
 *   <li>{@link #ACTIVE}, listed and usable.
 *   <li>{@link #SUSPENDED}, hidden and unusable for now, but recoverable (for example while rent is unpaid or
 *       a staff member is reviewing it); the row is retained.
 *   <li>{@link #ARCHIVED}, retired from normal use, kept only for history.
 * </ul>
 *
 * <p>This axis is orthogonal to {@link WarpAccess}: access controls who may use an active warp, status controls
 * whether it is usable at all. The persisted token is the constant's {@link #name()} (uppercase);
 * {@link #parse(String)} reads it back case-insensitively.
 */
public enum WarpStatus {
    ACTIVE,
    SUSPENDED,
    ARCHIVED;

    /**
     * Match a stored or user-supplied token to a constant, ignoring case and surrounding whitespace. Returns
     * an empty result, never throws, for {@code null}, blank, or unrecognised input.
     */
    public static Optional<WarpStatus> parse(@Nullable String token) {
        if (token == null) {
            return Optional.empty();
        }
        String normalised = token.strip().toUpperCase(Locale.ROOT);
        if (normalised.isEmpty()) {
            return Optional.empty();
        }
        for (WarpStatus status : values()) {
            if (status.name().equals(normalised)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
