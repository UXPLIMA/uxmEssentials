package com.uxplima.uxmessentials.staff.application.port;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.staff.domain.SavedLoadout;

/**
 * Outbound port for the durable storage of a player's captured pre-staff-mode loadout. The loadout is
 * DB-backed (never PDC) so it survives a restart or world rollback, the item-loss-safe invariant of staff
 * mode rests on this: the loadout is {@link #save saved} before the inventory is swapped, so a crash
 * mid-mode leaves the real loadout recoverable, and it is {@link #delete deleted} only after a successful
 * restore on exit.
 *
 * <p>The jOOQ implementation (a single-row-per-owner table, upsert on the owner key) lands in the
 * persistence adapter; the application depends only on this interface.
 */
public interface StaffLoadoutRepository {

    /** Store {@code loadout} for {@code owner}, replacing any existing row (upsert on the owner key). */
    void save(UUID owner, SavedLoadout loadout);

    /** The stored loadout for {@code owner}, or empty when none is held. */
    Optional<SavedLoadout> load(UUID owner);

    /** Remove {@code owner}'s stored loadout; a no-op when none is held. */
    void delete(UUID owner);
}
