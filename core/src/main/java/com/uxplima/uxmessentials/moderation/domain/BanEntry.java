package com.uxplima.uxmessentials.moderation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One row of the active-ban read model {@code /banlist} renders, a still-in-effect tempban (a {@code /ban}
 * records a far-future tempban row, so both permanent and timed bans live in one table). It is a flat
 * projection of a {@code moderation_tempbans} row, carrying the banned target, who issued it, the optional
 * reason, and the wall-clock expiry; the use case resolves the target's display name from the projection's
 * UUID. Read-only: there is no behaviour here, only the fields the list line interpolates.
 *
 * @param target the banned player's UUID
 * @param issuer who issued the ban
 * @param reason the optional free-text reason
 * @param until the wall-clock expiry (a far-future instant for a permanent {@code /ban})
 */
public record BanEntry(java.util.UUID target, Issuer issuer, Optional<String> reason, Instant until) {

    public BanEntry {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(until, "until");
    }
}
