package com.uxplima.uxmessentials.staff.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.port.StaffVanish;
import com.uxplima.uxmessentials.vanish.application.ToggleVanish;
import org.jspecify.annotations.NullMarked;

/**
 * The vanish-backed {@link StaffVanish}: it routes staff-mode vanish through the dedicated {@code vanish} context's
 * {@link ToggleVanish} use case, so staff mode never owns a second vanish state. A staff member who vanishes on entry
 * is vanished in the one authority every other context reads. The port asks for an <i>absolute</i> set (vanish on
 * enter, reveal on exit), which {@link ToggleVanish#setVanished} makes idempotent by toggling only when the live state
 * differs, leaving an already-correctly-vanished player untouched.
 *
 * <p>This impl is bound only when the vanish module is enabled; with vanish off the wiring binds {@link StaffVanish#NONE}
 * instead, so staff mode degrades to "vanish does nothing" rather than failing.
 */
@NullMarked
public final class VanishStaffVanish implements StaffVanish {

    private final ToggleVanish toggleVanish;

    public VanishStaffVanish(ToggleVanish toggleVanish) {
        this.toggleVanish = Objects.requireNonNull(toggleVanish, "toggleVanish");
    }

    @Override
    public void setVanished(PlayerRef who, boolean vanished) {
        Objects.requireNonNull(who, "who");
        toggleVanish.setVanished(who, vanished);
    }

    @Override
    public boolean isVanished(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return toggleVanish.isVanished(who);
    }
}
