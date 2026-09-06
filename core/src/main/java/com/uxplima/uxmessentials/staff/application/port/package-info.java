/**
 * The staff context's outbound ports: {@code StaffModeStore} for the in-memory active-mode marker (a
 * per-player {@code ConcurrentHashMap} in the adapter), {@code StaffLoadoutRepository} for the DB-backed
 * captured loadout (the item-loss-safe storage, never PDC), {@code StaffLoadoutCapture} for the Bukkit
 * inventory operations (snapshot/restore/apply-gadget-hotbar), and the soft-coupled seams that orchestrate
 * the existing modules and degrade to a {@code NONE} no-op when their source module is off: {@code
 * StaffVanish} (presence), {@code StaffInspector} (playerstate), and {@code StaffChannel} (messaging staff
 * audience). The application depends only on these interfaces; the persistence and bukkit adapters implement
 * them.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.staff.application.port;
