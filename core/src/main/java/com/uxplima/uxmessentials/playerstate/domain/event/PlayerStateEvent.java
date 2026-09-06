package com.uxplima.uxmessentials.playerstate.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * The playerstate context's sealed family of domain events. The per-context seal that closes the event set
 * without touching the shared {@code DomainEvent} marker. Every concrete implementation is a {@code record}
 * (value equality backs the in-process bus and equality-based test consumers), and the adapter bridges each
 * to a Bukkit event so other plugins and the audit log observe a state change without importing this package.
 *
 * <p>Names are past tense. A domain event records something that already happened ({@code GodToggled},
 * {@code Healed}), never an imperative command.
 */
public sealed interface PlayerStateEvent extends DomainEvent
        permits GodToggled, FlyToggled, GameModeChanged, SpeedChanged, Healed, Fed {}
