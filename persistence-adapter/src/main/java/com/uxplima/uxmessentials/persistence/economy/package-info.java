/**
 * The economy context's outbound persistence adapter. The native ledger's durable side over the generated
 * {@code economy_owners}, {@code wallet_balances}, and {@code transactions} tables. Every balance is a
 * first-class {@code (owner, currency, amount)} row, never an opaque JSON blob, so a {@code Wallet} rebuilds
 * from queryable rows and {@code /baltop} pushes its {@code ORDER BY amount DESC LIMIT ?} to the database.
 * Balances are DB-backed and survive a world rollback (invariant (d)); they never live in PDC.
 *
 * <p>The load-bearing rule is the guarded debit ({@code JooqWalletRepository}): a debit is one
 * {@code UPDATE … WHERE amount >= ?}, so the database serialises two concurrent debits and an over-draw
 * changes zero rows. The settle path coalesces through {@code DebouncedWalletWriter} (last-value-wins,
 * never the transactional path) and the append-only ledger history batches through {@code TransactionTelemetry}.
 * SQL is issued only through the typed jOOQ DSL, never string concatenation.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.economy;
