package com.uxplima.uxmessentials.staff.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.Freeze;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.staff.application.port.StaffFreeze;
import org.jspecify.annotations.NullMarked;

/**
 * The moderation-backed {@link StaffFreeze}: the FREEZE gadget routes through the existing moderation
 * {@link Freeze} use case so the freeze is audited and respects the exempt node, staff mode never owns a
 * second frozen state. The current state read goes straight to {@link Sanctions#isFrozen} (the same runtime
 * set moderation owns), so a toggle that finds the target frozen unfreezes and one that finds it free freezes.
 *
 * <p>Bound only when the moderation module is enabled; with moderation off the wiring binds
 * {@link StaffFreeze#NONE}, so the gadget degrades to "freeze is unavailable" rather than failing.
 */
@NullMarked
public final class ModerationStaffFreeze implements StaffFreeze {

    private final Freeze freeze;
    private final Sanctions sanctions;

    public ModerationStaffFreeze(Freeze freeze, Sanctions sanctions) {
        this.freeze = Objects.requireNonNull(freeze, "freeze");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
    }

    @Override
    public FreezeOutcome toggle(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        if (sanctions.isFrozen(target)) {
            return freeze.unfreeze(actor, target).isOk() ? FreezeOutcome.UNFROZEN : FreezeOutcome.UNAVAILABLE;
        }
        Result<Unit, ModerationError> result = freeze.freeze(actor, target);
        if (result.isOk()) {
            return FreezeOutcome.FROZEN;
        }
        // The only modelled freeze failure is an exempt target; map it to the dedicated outcome so the gadget
        // tells the staff member why nothing happened rather than reporting a generic unavailability.
        return result.errorOrThrow() == ModerationError.TARGET_EXEMPT
                ? FreezeOutcome.EXEMPT
                : FreezeOutcome.UNAVAILABLE;
    }

    @Override
    public boolean isFrozen(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        return sanctions.isFrozen(target);
    }
}
