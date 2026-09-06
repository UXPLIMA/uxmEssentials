package com.uxplima.uxmessentials.teleport.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.domain.BackLocation;

/**
 * Outbound port for a player's current {@code /back} return point. Each player has at most one capture
 * (the most recent supersedes the last), held as in-flight runtime state the teleport module drops on
 * {@code stop()} and re-arms on relog. Never PDC-stamped, since a {@code /back} point is transient
 * session state, not persisted data.
 */
public interface BackLocationStore {

    /** Record {@code location} as {@code who}'s current return point, replacing any previous capture. */
    void capture(PlayerRef who, BackLocation location);

    /** The player's current return point, or empty when none has been captured this session. */
    Optional<BackLocation> current(PlayerRef who);

    /** Drop the player's capture (on logout or after a successful {@code /back}). */
    void clear(PlayerRef who);
}
