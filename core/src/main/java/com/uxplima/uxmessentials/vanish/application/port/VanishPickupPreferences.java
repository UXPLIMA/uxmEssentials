package com.uxplima.uxmessentials.vanish.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for a player's "pick up items while vanished" preference, the {@code /vanish pickup} toggle. The
 * preference is per-player transient state that should survive a relog, so the adapter stamps it in PDC under a single
 * pre-created key (the sanctioned use for per-holder state), defaulting an unstamped player to the module's
 * {@code pickup-items} config value.
 *
 * <p>Only a vanished player's pickups are ever governed by this; a visible player picks up normally regardless. The
 * pickup listener reads {@link #picksUp} on the region thread the pickup fires on, and {@code /vanish pickup} flips it
 * through {@link #toggle}.
 */
public interface VanishPickupPreferences {

    /** Whether {@code who} picks up items while vanished; an unstamped player reads the {@code pickup-items} default. */
    boolean picksUp(PlayerRef who);

    /** Flip {@code who}'s pickup preference and return the new value; a no-op returning the default when offline. */
    boolean toggle(PlayerRef who);
}
