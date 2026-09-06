package com.uxplima.uxmessentials.poses.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port over a player's personal "may others sit on me" preference, the GSit-style opt-out that lets a
 * player refuse being used as a chair while server-wide player-sitting stays on. It is the second gate in front of
 * {@code StartPlayerSit}: the config {@code features.player-sit} switch is the operator's master toggle, this is the
 * individual's own veto.
 *
 * <p>Following GSit, the default is permissive: a player who never touched the setting <em>allows</em> being sat on,
 * and {@code /poses toggle} flips it. The adapter backs it with per-holder PDC. The sanctioned use for transient
 * per-player state, like cooldown and kit-claim stamps, read and written off any hot path.
 */
public interface PlayerSitPreferences {

    /** Whether {@code who} currently allows other players to sit on them; a player who never set it allows it. */
    boolean allowsSitting(PlayerRef who);

    /** Flip {@code who}'s preference, returning the new "allows sitting" value ({@code true} = now allows). */
    boolean toggle(PlayerRef who);
}
