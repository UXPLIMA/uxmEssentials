/**
 * Pure domain of the economy bounded context: the plugin's canonical worked DDD example. It owns the
 * {@code Money} and {@code Currency} value objects (exact, {@code BigDecimal}-backed, never floating point),
 * the {@code Wallet} aggregate that holds a balance <em>per {@code Currency}</em> and enforces the
 * never-negative / never-over-max / never-cross-currency invariants in one place, the {@code Transaction}
 * entity it mints, the sealed {@code TransferResult} outcome, and the sealed {@code EconomyEvent} family.
 * No Bukkit, Paper, Kyori, logging, Vault, or Treasury type appears here. The model is built from value
 * objects and the cross-cutting kernel primitives ({@code PlayerRef}, {@code Result}, {@code Unit}). The
 * ArchUnit fence {@code economyDomainHasNoProviderSdk} keeps the provider SDKs out.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.economy.domain;
