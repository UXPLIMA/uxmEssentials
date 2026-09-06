package com.uxplima.uxmessentials.vaults.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * The vaults context's sealed family of domain events. The per-context seal that closes the event set without
 * touching the shared {@code DomainEvent} marker. Every concrete implementation is a {@code record} (value
 * equality backs the in-process bus and equality-based test consumers), and the adapter bridges each to a
 * Bukkit event so other plugins observe vault activity without importing this package.
 *
 * <p>Names are past tense. A domain event records something that already happened ({@code VaultOpened},
 * {@code VaultContentsChanged}), never an imperative command.
 */
public sealed interface VaultEvent extends DomainEvent permits VaultOpened, VaultContentsChanged {}
