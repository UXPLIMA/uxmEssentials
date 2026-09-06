package com.uxplima.uxmessentials.nametags.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Soft-coupled gate to the presence context: whether a {@code viewer} may see the nametag of a {@code wearer}.
 * A vanished wearer's nametag is hidden from anyone who cannot see them. Staff holding the vanish-see node keep
 * seeing it, so the nametag tracks the same visibility graph the {@code /tpa} listing and {@code /msg} resolution
 * already honour (docs/02-concurrency.md §6.9: vanish visibility integrates with the player-facing surfaces).
 *
 * <p>The coupling is soft: when the presence module is disabled (or has not yet landed) the wiring binds
 * {@link #ALWAYS_VISIBLE}, so the nametag renderer degrades to "everyone can see everyone" rather than failing
 * mirroring messaging's {@code VanishVisibility}, whose {@code ALWAYS_VISIBLE} binding is the presence-off default.
 * Note the polarity is the renderer's natural one (can this viewer see the wearer?), the inverse of messaging's
 * {@code isHiddenFrom}, because the renderer builds an eligible-viewer set rather than answering a hidden-from query.
 */
public interface NametagVanish {

    /** A policy under which everyone can always see everyone: the binding when presence is disabled. */
    NametagVanish ALWAYS_VISIBLE = (viewer, wearer) -> true;

    /** True when {@code viewer} may see {@code wearer}'s nametag (false when vanish hides the wearer from them). */
    boolean canSee(PlayerRef viewer, PlayerRef wearer);
}
