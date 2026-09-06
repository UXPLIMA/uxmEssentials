package com.uxplima.uxmessentials.poses.application;

import java.util.Objects;

import com.uxplima.uxmessentials.poses.application.port.PlayerSitPreferences;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Flips a player's personal "may others sit on me" preference: the {@code /poses toggle} verb. It is a thin
 * front over {@link PlayerSitPreferences#toggle}, returning the new state so the command renders the right message
 * ({@code now allowed} / {@code now refused}). The default is permissive, so a first toggle turns refusal on.
 */
public final class TogglePlayerSit {

    private final PlayerSitPreferences preferences;

    public TogglePlayerSit(PlayerSitPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    /** Flip {@code who}'s preference, returning the new "allows sitting" value ({@code true} = now allows). */
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return preferences.toggle(who);
    }
}
