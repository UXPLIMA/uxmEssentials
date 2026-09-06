package com.uxplima.uxmessentials.playerwarps.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A single player's ban from one warp: who is banned, when the ban lifts (if ever), why, who imposed it, and
 * when. A ban is keyed on the warp id plus this {@link #player}; re-banning the same player updates the row
 * (the store owns that upsert) so there is at most one ban per player per warp.
 *
 * <p>An {@link #until} of {@link Optional#empty()} is a <em>permanent</em> ban: it never lifts. A present
 * {@code until} is the absolute instant the ban expires, stored as a wall-clock moment rather than a duration
 * so a slow read can never silently extend it. The {@link #reason} and {@link #bannedBy} are optional because a
 * ban may be imposed by the console or without a stated reason.
 *
 * @param player the banned player's uuid
 * @param until the instant the ban lifts, or empty for a permanent ban
 * @param reason the stated reason, if any
 * @param bannedBy the uuid of whoever imposed it, if a player did
 * @param bannedAt when the ban was imposed
 */
public record BanRecord(
        UUID player, Optional<Instant> until, Optional<String> reason, Optional<UUID> bannedBy, Instant bannedAt) {

    public BanRecord {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(until, "until");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(bannedBy, "bannedBy");
        Objects.requireNonNull(bannedAt, "bannedAt");
    }

    /**
     * True while the ban is still in force at {@code now}. A permanent ban (absent {@link #until}) is always
     * active; a timed ban is active up to but not including its {@code until} instant, so a ban whose expiry has
     * passed reads as inactive.
     */
    public boolean isActiveAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return until.isEmpty() || now.isBefore(until.get());
    }
}
