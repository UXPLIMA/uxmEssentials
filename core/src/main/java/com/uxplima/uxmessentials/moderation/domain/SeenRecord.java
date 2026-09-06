package com.uxplima.uxmessentials.moderation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The last-seen / last-IP record for one player. What {@code /seen} and {@code /seenip} report and what the
 * alt-detection check matches against. Upserted on join/quit: the {@code name} is the most recent name (so a
 * rename-aware {@code /seen} renders the current name), {@code lastIp} the most recent connecting address (an
 * indexed column the alt-detection lookup matches other UUIDs against), {@code firstSeen} the first-join
 * instant and {@code lastSeen} the last activity instant.
 *
 * @param player the player this record belongs to (carries the current name)
 * @param lastIp the most recent connecting address, or empty if never recorded
 * @param firstSeen the first-join instant
 * @param lastSeen the last-activity instant
 */
public record SeenRecord(PlayerRef player, Optional<String> lastIp, Instant firstSeen, Instant lastSeen) {

    public SeenRecord {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(lastIp, "lastIp");
        Objects.requireNonNull(firstSeen, "firstSeen");
        Objects.requireNonNull(lastSeen, "lastSeen");
    }

    /** A first-join record: first-seen and last-seen are the same connect instant. */
    public static SeenRecord firstJoin(PlayerRef player, Optional<String> ip, Instant at) {
        return new SeenRecord(player, ip, at, at);
    }

    /** This record advanced to a fresh activity instant, keeping the original first-join. */
    public SeenRecord touched(Instant at, Optional<String> ip) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(ip, "ip");
        return new SeenRecord(player, ip.or(() -> lastIp), firstSeen, at);
    }
}
