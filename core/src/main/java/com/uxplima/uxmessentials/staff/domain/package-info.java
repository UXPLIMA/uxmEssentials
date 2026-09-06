/**
 * Pure domain of the staff bounded context: the {@code StaffModeState} marker (active flag plus the named
 * mode profile), the {@code SavedLoadout} value object (the captured pre-mode inventory/armour/off-hand
 * blobs plus the held slot, experience, game mode, flight, and potion-effect scalars), and the sealed
 * {@code StaffEvent} family ({@code StaffModeEntered}, {@code StaffModeExited}, {@code StaffChatSent}). No
 * Bukkit, Paper, Kyori, or logging type appears here. The item blobs are opaque {@code byte[]} the adapter
 * encodes/decodes, and nothing here is a sanction.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.staff.domain;
