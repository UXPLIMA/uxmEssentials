package com.uxplima.uxmessentials.teleport.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port that shields a player for a short window right after a random teleport lands, so they do
 * not die to fall damage or a mob before the far terrain finishes rendering. The adapter applies the
 * configured potion effects (Resistance, Slow-Falling) and arms a brief no-fall-damage guard for the
 * configured duration; the application only decides <em>when</em> to trigger it, on a successful RTP
 * arrival and nowhere else.
 *
 * <p>The Bukkit potion/fall-guard details (which effects, how long, cancelling the fall-damage event) all
 * live behind this seam, so the use cases never touch a {@code PotionEffect}.
 */
public interface ArrivalGrace {

    /** A no-op grace: nothing is applied. Used where no shield is wanted or in pure tests. */
    ArrivalGrace NONE = who -> {
        // No grace configured.
    };

    /** Apply the arrival grace window to {@code who}; a no-op when the player is offline or grace is off. */
    void applyOnArrival(PlayerRef who);
}
