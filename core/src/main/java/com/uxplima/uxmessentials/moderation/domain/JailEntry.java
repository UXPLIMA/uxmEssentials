package com.uxplima.uxmessentials.moderation.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One row of the active-jail read model {@code /jailedplayers} renders, a still-in-effect jail sentence. It
 * is a flat projection of a {@code moderation_jails} row, carrying the jailed target, the named jail, who
 * issued it, the optional reason, and how the sentence ends: a permanent jail has neither {@link #until()}
 * nor {@link #remaining()}, a wall-clock sentence carries its {@code until} expiry, and an online-only
 * sentence carries the {@code remaining} online time still to serve. The use case resolves the target's
 * display name from the projection's UUID. Read-only: only the fields the list line interpolates.
 *
 * @param target the jailed player's UUID
 * @param jail the named jail location the target is held in
 * @param issuer who issued the jail
 * @param reason the optional free-text reason
 * @param until the wall-clock expiry for a wall-clock sentence, or empty otherwise
 * @param remaining the online time still to serve for an online-only sentence, or empty otherwise
 */
public record JailEntry(
        java.util.UUID target,
        String jail,
        Issuer issuer,
        Optional<String> reason,
        Optional<Instant> until,
        Optional<Duration> remaining) {

    public JailEntry {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(jail, "jail");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(until, "until");
        Objects.requireNonNull(remaining, "remaining");
    }
}
