package com.uxplima.uxmessentials.staff.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Soft-coupled seam to the playerstate context: open a target's inventory for an inspecting staff member
 * the EXAMINE gadget. Staff mode does not own the inventory-view machinery; it orchestrates the existing
 * playerstate module's {@code OpenContainer#openInventory} through this port.
 *
 * <p>The coupling is soft: when the playerstate module is disabled (or has not yet landed) the wiring binds
 * {@link #NONE}, so the EXAMINE gadget degrades to a no-op (or to the info line the adapter still shows)
 * rather than failing: mirroring messaging's {@code MutePolicy.NEVER}.
 */
public interface StaffInspector {

    /** A no-op inspector: the binding when playerstate is disabled. */
    StaffInspector NONE = (looker, target) -> {};

    /** Open {@code target}'s inventory in {@code looker}'s screen. */
    void inspect(PlayerRef looker, PlayerRef target);
}
