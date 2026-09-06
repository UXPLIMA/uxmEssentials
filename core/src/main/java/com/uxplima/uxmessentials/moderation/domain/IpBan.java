package com.uxplima.uxmessentials.moderation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One banned IP address, a row in the IP ban-list ({@code /banip}). The login listener resolves a
 * connecting address against this set before player data loads. A {@code null} {@code until} is a permanent
 * IP ban; a non-null one a timed ban. {@code target} records the UUID the ban was applied against if any
 * ({@code /banip <player>} resolves one, {@code /banip <ip>} leaves it empty), so the audit line stays
 * replayable.
 *
 * @param ip the banned address literal (the identity)
 * @param until the wall-clock expiry, or empty for a permanent ban
 * @param reason the optional free-text reason
 * @param target the UUID the ban was applied against, if any
 * @param issuer who issued the ban
 * @param issuedAt when the ban was issued
 */
public record IpBan(
        String ip,
        Optional<Instant> until,
        Optional<String> reason,
        Optional<UUID> target,
        Issuer issuer,
        Instant issuedAt) {

    public IpBan {
        Objects.requireNonNull(ip, "ip");
        Objects.requireNonNull(until, "until");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(issuedAt, "issuedAt");
        if (ip.isBlank()) {
            throw new IllegalArgumentException("banned ip must not be blank");
        }
    }

    /** True when this ban still bars the connecting address at {@code now} (permanent always). */
    public boolean isActiveAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return until.map(now::isBefore).orElse(true);
    }
}
