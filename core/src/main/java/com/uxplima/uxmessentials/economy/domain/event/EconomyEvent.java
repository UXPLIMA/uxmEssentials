package com.uxplima.uxmessentials.economy.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * The economy context's sealed family of domain events. The per-context seal that closes the event set
 * without touching the shared {@code DomainEvent} marker. The {@link com.uxplima.uxmessentials.economy.domain.Wallet}
 * aggregate is the only thing that emits these, and exactly one is raised per applied or refused change:
 * a credit raises {@link WalletCredited}, a debit raises {@link WalletDebited}, a debit refused for
 * insufficient funds raises {@link WalletRejected} (a first-class event, not a thrown exception, so audit
 * and the requesting context observe a refusal the same way they observe success).
 *
 * <p>Every concrete implementation is a {@code record} (value equality backs the in-process bus and
 * equality-based test consumers); the adapter bridges each to a Bukkit event so foreign plugins observe
 * balance changes without importing this package. Names are past tense. An event records something that
 * already happened, never an imperative command.
 */
public sealed interface EconomyEvent extends DomainEvent
        permits WalletCredited,
                WalletDebited,
                WalletRejected,
                LoanDisbursed,
                LoanRepaid,
                BankDeposited,
                BankWithdrawn {}
