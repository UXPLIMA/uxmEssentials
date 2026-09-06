package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Duration;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the presence placeholders, the bare {@code afk}, {@code afk_duration}
 * and {@code vanished} keys plus the {@code presence_*} family ({@code presence_nickname},
 * {@code presence_realname}, {@code presence_afk_reason}). It is an adapter over the presence context's
 * in-memory {@code PresenceStore} and the live player's display name, wired during bootstrap; when the
 * presence module is disabled the seam is absent and the placeholders degrade.
 *
 * <p>Presence is per-session in-memory state. An offline player has no tracked presence, so the seam
 * returns an empty snapshot: the expansion renders the offline/empty default for it.
 */
public interface PresencePlaceholders {

    /** A point-in-time view of {@code who}'s presence, or empty when they are not currently tracked. */
    Optional<Snapshot> snapshot(PlayerRef who);

    /**
     * The immutable presence facts the placeholders read: whether the player is AFK, how long they have
     * been AFK as of the read, the reason shown for a manual {@code /afk}, whether they are vanished, and
     * the player's current display name (nickname, or the account name when none is set).
     *
     * @param afk whether {@code who} is currently AFK
     * @param afkFor how long {@code who} has been AFK as of the read, {@link Duration#ZERO} when not AFK
     * @param afkReason the reason given for a manual {@code /afk}, or empty for auto-AFK and when not AFK
     * @param vanished whether {@code who} is currently vanished
     * @param nickname {@code who}'s current display name, their {@code /nick} if set, else the account name
     */
    record Snapshot(boolean afk, Duration afkFor, Optional<String> afkReason, boolean vanished, String nickname) {

        public Snapshot {
            java.util.Objects.requireNonNull(afkFor, "afkFor");
            java.util.Objects.requireNonNull(afkReason, "afkReason");
            java.util.Objects.requireNonNull(nickname, "nickname");
        }
    }
}
