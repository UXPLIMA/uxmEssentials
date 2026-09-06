package com.uxplima.uxmessentials.presence.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * The presence context's sealed family of domain events. The per-context seal that closes the event set
 * without touching the shared {@code DomainEvent} marker. Every concrete implementation is a {@code record}
 * (value equality backs the in-process bus and equality-based test consumers), and the adapter bridges each to
 * a Bukkit event so other plugins observe presence changes without importing this package.
 *
 * <p>Names are past tense. A domain event records something that already happened ({@code WentAfk},
 * {@code ReturnedFromAfk}), never an imperative command. An AFK transition fires whether it was the player's own
 * {@code /afk} or the idle sweep that flipped them; the {@code automatic} flag on {@link WentAfk} distinguishes the
 * two for placeholders and audit lines. Vanish moved to its own {@code vanish} context and is no longer a presence
 * event.
 */
public sealed interface PresenceEvent extends DomainEvent permits WentAfk, ReturnedFromAfk {}
