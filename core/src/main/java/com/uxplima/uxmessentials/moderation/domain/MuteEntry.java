package com.uxplima.uxmessentials.moderation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One row of the active-mute read model {@code /mutelist} renders, a still-in-effect mute. It is a flat
 * projection of a {@code moderation_mutes} row, carrying the muted target, who issued it, the optional reason,
 * and the expiry: a permanent mute has an empty {@link #until()}, a timed mute carries its wall-clock expiry.
 * The use case resolves the target's display name from the projection's UUID. Read-only. Only the fields the
 * list line interpolates.
 *
 * @param target the muted player's UUID
 * @param issuer who issued the mute
 * @param reason the optional free-text reason
 * @param until the wall-clock expiry for a timed mute, or empty for a permanent mute
 */
public record MuteEntry(java.util.UUID target, Issuer issuer, Optional<String> reason, Optional<Instant> until) {

    public MuteEntry {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(until, "until");
    }
}
