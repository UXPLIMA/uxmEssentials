package com.uxplima.uxmessentials.messaging.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Soft-coupled gate to the vanish context: whether a {@code target} is hidden from a {@code viewer} by
 * vanish. A vanished player is hidden from {@code /msg} target resolution. A sender who cannot see them
 * resolves them as if they were offline (docs/02-concurrency.md §6.9: vanish visibility integrates with
 * messaging). Staff holding the vanish-see node still see, and so can still message, a vanished target.
 *
 * <p>The coupling is soft: when the vanish module is disabled (or has not yet landed) the wiring binds
 * {@link #ALWAYS_VISIBLE}, so messaging degrades to "no one is hidden" rather than failing, mirroring the
 * teleport context, whose {@code /tpa} is likewise vanish-aware through a check over the same authority that
 * degrades when vanish is off.
 */
public interface VanishVisibility {

    /** A policy under which nothing is ever hidden: the binding when vanish is disabled. */
    VanishVisibility ALWAYS_VISIBLE = (viewer, target) -> false;

    /** True when {@code target} is vanished and {@code viewer} cannot see them (so cannot message them). */
    boolean isHiddenFrom(PlayerRef viewer, PlayerRef target);
}
