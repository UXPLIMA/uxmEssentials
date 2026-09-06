package com.uxplima.uxmessentials.staff.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * The staff context's sealed family of domain events. The per-context seal that closes the event set
 * without touching the shared {@code DomainEvent} marker. Every concrete implementation is a
 * {@code record} (value equality backs the in-process bus and equality-based test consumers), and the
 * adapter bridges each to a Bukkit event so other plugins observe staff-mode changes and staff chat
 * without importing this package.
 *
 * <p>Names are past tense. A domain event records something that already happened
 * ({@code StaffModeEntered}, {@code StaffChatSent}), never an imperative command. Nothing here represents a
 * sanction: staff mode only orchestrates the existing modules, it never mutes, bans, kicks, or warns.
 */
public sealed interface StaffEvent extends DomainEvent permits StaffModeEntered, StaffModeExited, StaffChatSent {}
