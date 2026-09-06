package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code staff_*} placeholders. It is an adapter over the staff context's
 * existing handles wired during bootstrap. The {@code StaffModeStore} marker that tracks who is in staff mode,
 * and the online-staff enumeration over the {@code uxmessentials.staff.member} marker that {@code /stafflist} and
 * the presence {@code /staff} roster agree on. When the staff module is disabled the seam is absent and the
 * placeholders degrade to the dash.
 *
 * <p>{@link #inStaffMode(PlayerRef)} reads the live, session-scoped staff-mode marker, so it answers for an
 * online requester and degrades to {@code no} for an offline one (no marker without a session). {@link
 * #onlineStaffCount()} counts the online staff-marker holders. A server-wide read that is the same for every
 * requester, so it does not depend on who asks.
 */
public interface StaffPlaceholders {

    /** Whether {@code who} is currently in staff mode (the live {@code /staffmode} marker). */
    boolean inStaffMode(PlayerRef who);

    /** How many online players hold the staff-member marker, the {@code /stafflist} / {@code /staff} roster size. */
    int onlineStaffCount();
}
