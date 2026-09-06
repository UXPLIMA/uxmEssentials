package com.uxplima.uxmessentials.homes.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * The homes context's sealed family of domain events. The per-context seal that closes the event set
 * without touching the shared {@code DomainEvent} marker. Every concrete implementation is a
 * {@code record} (value equality backs the in-process bus and equality-based test consumers), and the
 * adapter bridges each to a Bukkit event so other plugins observe home changes without importing this
 * package.
 *
 * <p>Names are past tense. A domain event records something that already happened
 * ({@code HomeCreated}, {@code HomeDeleted}), never an imperative command.
 */
public sealed interface HomeEvent extends DomainEvent
        permits HomeCreated,
                HomeDeleted,
                HomeLimitReached,
                HomeRelocated,
                HomeRenamed,
                HomeIconChanged,
                HomeVisibilityChanged {}
