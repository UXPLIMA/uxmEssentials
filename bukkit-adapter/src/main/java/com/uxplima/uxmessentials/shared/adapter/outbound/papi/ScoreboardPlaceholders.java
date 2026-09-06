package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code scoreboard_*} placeholders. It is an adapter over the scoreboard
 * context's {@code ScoreboardVisibilityStore} (the PDC-backed per-player "hidden" bit that {@code /scoreboard}
 * flips), wired during scoreboard wiring. When the scoreboard module is disabled the seam is absent and the
 * placeholder degrades to the dash.
 *
 * <p>The only read is whether the requesting player currently sees the sidebar. The store backs the preference on the
 * live player, so it holds no value for an offline requester (who has no board to show); the resolver's offline guard
 * degrades the placeholder to the dash for them.
 */
public interface ScoreboardPlaceholders {

    /**
     * Whether {@code who}'s sidebar display is currently shown: that is, not hidden by {@code /scoreboard}. A player
     * who never toggled is shown, so the default is {@code true}.
     */
    boolean visible(PlayerRef who);

    /**
     * The board {@code who}'s sidebar is currently drawn from, or empty when they are shown none: hidden by the
     * toggle, suppressed in this world, or matching no board. Reported from what the renderer last applied rather
     * than re-selecting, so it costs nothing per read and cannot re-evaluate a condition that itself expands a
     * placeholder.
     */
    Optional<String> board(PlayerRef who);
}
