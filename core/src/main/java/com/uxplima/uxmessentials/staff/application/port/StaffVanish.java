package com.uxplima.uxmessentials.staff.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Soft-coupled seam to the presence context: set a staff member's vanish state when they enter or leave
 * staff mode. Staff mode does not own vanish; it orchestrates the existing presence module's
 * {@code ToggleVanish}/{@code PresenceStore} through this port so there is no second vanish state to keep in
 * sync.
 *
 * <p>The coupling is soft: when the presence module is disabled (or has not yet landed) the wiring binds
 * {@link #NONE}, so staff mode degrades to "vanish does nothing" rather than failing, the same
 * degrade-when-the-other-module-is-off pattern as messaging's {@code MutePolicy.NEVER}.
 */
public interface StaffVanish {

    /** A no-op vanish: the binding when presence is disabled; reads as never-vanished. */
    StaffVanish NONE = new StaffVanish() {
        @Override
        public void setVanished(PlayerRef who, boolean vanished) {}

        @Override
        public boolean isVanished(PlayerRef who) {
            return false;
        }
    };

    /** Set {@code who}'s vanish state to {@code vanished}. */
    void setVanished(PlayerRef who, boolean vanished);

    /**
     * Whether {@code who} is currently vanished. Read on enter so the pre-mode vanish state can be captured and
     * restored on exit (rather than hard-revealing a player who was already vanished via {@code /vanish}). The
     * {@link #NONE} binding, and the default, reports never-vanished.
     */
    default boolean isVanished(PlayerRef who) {
        return false;
    }
}
