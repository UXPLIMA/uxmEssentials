/**
 * The economy context's sealed {@code EconomyEvent} family. {@code WalletCredited}, {@code WalletDebited},
 * {@code WalletRejected}: the only events a {@code Wallet} emits, one per applied or refused change. Each
 * is a {@code record} (value equality backs the in-process bus and equality-based test consumers); the
 * adapter bridges each to a Bukkit event so foreign plugins observe balance changes without importing this
 * package. The seal lives here, on the per-context sub-interface, not on the shared {@code DomainEvent}
 * marker.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.economy.domain.event;
