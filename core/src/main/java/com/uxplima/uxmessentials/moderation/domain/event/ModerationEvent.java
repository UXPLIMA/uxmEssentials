package com.uxplima.uxmessentials.moderation.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * The moderation context's sealed family of domain events. The per-context seal that closes the event set
 * without touching the shared {@code DomainEvent} marker. Every concrete implementation is a {@code record}
 * (value equality backs the in-process bus and equality-based test consumers), and the adapter bridges each
 * to a Bukkit event so other plugins observe sanctions without importing this package.
 *
 * <p>Names are past tense. A domain event records something that already happened ({@code PlayerMuted},
 * {@code PlayerJailed}, {@code PlayerTempbanned}, {@code PlayerWarned}), never an imperative command.
 */
public sealed interface ModerationEvent extends DomainEvent
        permits PlayerMuted,
                PlayerUnmuted,
                PlayerJailed,
                PlayerUnjailed,
                PlayerTempbanned,
                PlayerWarned,
                PlayerIpBanned,
                AltDetected,
                JailLocationDefined,
                JailLocationRemoved {}
