package com.uxplima.uxmessentials.staff.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Soft-coupled seam to the moderation context: freeze or unfreeze a target, the FREEZE gadget. Staff mode
 * does not own the freeze machinery; it orchestrates the existing moderation module's freeze handling through
 * this port, so there is no second frozen-state to keep in sync with moderation's.
 *
 * <p>The coupling is soft: when the moderation module is disabled (or has not yet landed) the wiring binds
 * {@link #NONE}, so the FREEZE gadget degrades to "freeze is unavailable" rather than failing, the same
 * degrade-when-the-other-module-is-off pattern as {@link StaffVanish#NONE} and {@link StaffInspector#NONE}.
 */
@NullMarked
public interface StaffFreeze {

    /** A no-op freeze: the binding when moderation is disabled; reports unavailable and never-frozen. */
    StaffFreeze NONE = new StaffFreeze() {
        @Override
        public FreezeOutcome toggle(PlayerRef actor, PlayerRef target) {
            return FreezeOutcome.UNAVAILABLE;
        }

        @Override
        public boolean isFrozen(PlayerRef target) {
            return false;
        }
    };

    /** Freezes {@code target} if not frozen, unfreezes if frozen. Returns what happened. */
    FreezeOutcome toggle(PlayerRef actor, PlayerRef target);

    /** Whether {@code target} is currently frozen; the {@link #NONE} binding reports never-frozen. */
    boolean isFrozen(PlayerRef target);

    /** The result of a {@link #toggle(PlayerRef, PlayerRef)}: the new state, an exemption, or unavailability. */
    enum FreezeOutcome {
        FROZEN,
        UNFROZEN,
        EXEMPT,
        UNAVAILABLE
    }
}
