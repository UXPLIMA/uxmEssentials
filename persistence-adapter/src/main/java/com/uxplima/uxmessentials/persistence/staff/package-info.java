/**
 * The staff context's outbound persistence adapter: the jOOQ {@link
 * com.uxplima.uxmessentials.persistence.staff.JooqStaffLoadoutRepository} over the generated V29 {@code
 * staff_loadout} table (a single row per owner, upsert on the player key), and the {@link
 * com.uxplima.uxmessentials.persistence.staff.StaffLoadoutRows} anti-corruption mapping, the queryable
 * held-slot/exp/game-mode/flight scalars plus the four base64-encoded opaque item/effect regions. This is the
 * durable, item-loss-safe home of a player's pre-staff-mode loadout: it is committed here before the inventory
 * is swapped and read back to restore it on exit, so it survives a restart or world rollback (never PDC).
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.staff;
