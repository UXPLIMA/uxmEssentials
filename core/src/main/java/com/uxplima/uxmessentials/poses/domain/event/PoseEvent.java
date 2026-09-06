package com.uxplima.uxmessentials.poses.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * The poses context's sealed family of domain events. The per-context seal that closes the event set without
 * touching the shared {@code DomainEvent} marker. Every concrete implementation is a {@code record} (value
 * equality backs the in-process bus and equality-based test consumers), and the adapter bridges each to a
 * cancellable Bukkit event so other plugins can observe and veto a pose without importing this package.
 *
 * <p>Names are past tense. A domain event records something that already happened ({@code PoseStarted},
 * {@code PoseEnded}), never an imperative command.
 */
public sealed interface PoseEvent extends DomainEvent permits PoseStarted, PoseEnded {}
