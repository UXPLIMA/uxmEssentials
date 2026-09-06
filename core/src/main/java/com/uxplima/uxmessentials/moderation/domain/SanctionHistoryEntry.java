package com.uxplima.uxmessentials.moderation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One row of the append-only sanction history. A ban/unban/mute/unmute applied to a target, the projection a
 * {@code /banhistory} or {@code /mutehistory} line renders. The history is never updated or overwritten, so a
 * target's full disciplinary record is preserved and a review lists it newest-first.
 *
 * <p>The {@link #action} fixes which family a row belongs to (ban vs mute); the remaining fields carry the
 * detail one line interpolates. A present {@link #expiry} marks a timed sanction (a {@code /tempban} or
 * {@code /tempmute}), an empty one a permanent sanction or a lift. A present {@link #ip} marks an IP-scoped
 * ban row (a {@code /banip}/{@code /unbanip}); for those the {@link #target} is the nil UUID when the address
 * was banned without a resolved player.
 *
 * @param action which sanction kind this row records
 * @param target the affected player's UUID, or the nil UUID for an IP-only ban with no resolved player
 * @param actor who applied the sanction
 * @param reason the optional free-text reason
 * @param at when the sanction was applied
 * @param expiry the wall-clock expiry for a timed sanction, or empty for a permanent one or a lift
 * @param ip the banned address for an IP-scoped row, or empty for a UUID-scoped row
 */
public record SanctionHistoryEntry(
        SanctionAction action,
        UUID target,
        Issuer actor,
        Optional<String> reason,
        Instant at,
        Optional<Instant> expiry,
        Optional<String> ip) {

    public SanctionHistoryEntry {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(expiry, "expiry");
        Objects.requireNonNull(ip, "ip");
    }
}
