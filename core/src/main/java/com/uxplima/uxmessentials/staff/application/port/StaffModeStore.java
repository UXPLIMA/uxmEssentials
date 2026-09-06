package com.uxplima.uxmessentials.staff.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port holding the thin "is this player in staff mode, and under which profile" marker. The
 * captured loadout itself is not here. It is DB-backed in {@link StaffLoadoutRepository} so it survives a
 * restart, so this store only tracks the transient active flag and the mode name.
 *
 * <p>The adapter backs this with a {@code ConcurrentHashMap<UUID, StaffModeState>} mutated through
 * {@code compute}/{@code computeIfAbsent}, the per-player-keyed concurrency pattern the project mandates.
 */
public interface StaffModeStore {

    /** Whether {@code who} is currently in staff mode. */
    boolean isActive(PlayerRef who);

    /** Mark {@code who} as in staff mode under the profile {@code modeName}. */
    void setActive(PlayerRef who, String modeName);

    /** Drop {@code who}'s staff-mode marker; they are no longer in staff mode. */
    void clear(PlayerRef who);
}
