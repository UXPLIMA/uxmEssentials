package com.uxplima.uxmessentials.staff.domain;

import java.util.Objects;

/**
 * The in-memory fact that a player is currently in staff mode, under a named mode profile. The captured
 * loadout itself does not live here. It is DB-backed in the {@code StaffLoadoutRepository} so it survives
 * a restart or world rollback, so this state is only the thin "are they in staff mode, and under which
 * profile" marker the gadgets and listeners consult.
 *
 * @param active whether the player is currently in staff mode
 * @param modeName the name of the active mode profile; never blank
 */
public record StaffModeState(boolean active, String modeName) {

    public StaffModeState {
        Objects.requireNonNull(modeName, "modeName");
        if (modeName.isBlank()) {
            throw new IllegalArgumentException("modeName must not be blank");
        }
    }
}
