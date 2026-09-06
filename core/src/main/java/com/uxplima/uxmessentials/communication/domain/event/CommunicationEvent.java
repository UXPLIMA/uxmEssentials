package com.uxplima.uxmessentials.communication.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * The communication context's sealed family of domain events. The per-context seal that closes the event set
 * without touching the shared {@code DomainEvent} marker. Every concrete implementation is a {@code record}
 * (value equality backs the in-process bus and equality-based test consumers), and the adapter bridges each to a
 * Bukkit event so other plugins observe communication changes without importing this package.
 *
 * <p>Names are past tense. A domain event records something that already happened. {@link BroadcastOptOutToggled}
 * fires when a player flips their announcer subscription with {@code /broadcasttoggle}; {@link AnnouncerReloaded}
 * fires when the operator reloads the announcer schedule. Neither carries operator template content: they record
 * a state transition, not the rendered text.
 */
public sealed interface CommunicationEvent extends DomainEvent permits BroadcastOptOutToggled, AnnouncerReloaded {}
