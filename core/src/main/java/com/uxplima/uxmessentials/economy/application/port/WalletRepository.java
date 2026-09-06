package com.uxplima.uxmessentials.economy.application.port;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Outbound port for the durable ledger, the read/write seam behind the native {@code EconomyProvider}. Each
 * per-currency balance is a first-class {@code (uuid, currency, balance)} row, never an opaque JSON blob
 * (the persistence anti-pattern list), so a {@link Wallet} loads from queryable rows and {@code /baltop}
 * pushes its {@code ORDER BY balance DESC LIMIT ?} to the database. The jOOQ adapter behind this port mirrors
 * {@code JooqHomeRepository}; the load-bearing transfer/debit guard ({@code docs/02-concurrency.md} §6.7)
 * lives below it, expressed as the guarded {@code UPDATE} the contract suite proves.
 *
 * <p>This is where invariant (d) is honoured at the source: balances are DB-backed, never PDC, so they
 * survive a world rollback. A cache decorator may sit in front of this port for offline reads, but it is
 * never an authority: the guarded debit/credit path always reads the live row inside its transaction.
 */
public interface WalletRepository {

    /** The owner's full wallet (all per-currency balances), or empty when they have no row yet. */
    Optional<Wallet> findByOwner(PlayerRef owner);

    /**
     * Materialise the owner row if absent (the {@code ensureUserExists} upsert) so an offline-target write
     * cannot break a foreign key; idempotent. Returns the (possibly newly created) owner's wallet.
     */
    Wallet ensureOwner(PlayerRef owner);

    /** Last-write-wins settle of a single {@code (uuid, currency)} balance (the debounced/coalesced path). */
    void upsertBalance(PlayerRef owner, Money balance);

    /**
     * Atomic two-sided move in one transaction: the guarded debit of {@code from} and the clamp-checked credit
     * of {@code to} commit together or not at all. Returns {@link TransferError#INSUFFICIENT_FUNDS} when the
     * guarded {@code UPDATE} changed no rows (the database, not the JVM, serialises the contention) or
     * {@link TransferError#BALANCE_MAX_EXCEEDED} when crediting {@code to} would push it past the currency's
     * {@code max-balance}, in which case the debit is rolled back and nothing moves.
     */
    Result<Unit, TransferError> transfer(PlayerRef from, PlayerRef to, Money amount);

    /**
     * Guarded single-sided debit in one transaction: succeeds only if funds suffice, otherwise
     * {@link TransferError#INSUFFICIENT_FUNDS} with no row changed. The concurrent double-spend guard.
     */
    Result<Unit, TransferError> debit(PlayerRef owner, Money amount);

    /** Single-sided credit in one transaction, honouring the currency's {@code max-balance} clamp. */
    Result<Unit, TransferError> credit(PlayerRef owner, Money amount);

    /**
     * Atomic cross-currency conversion for one owner in a single transaction: the guarded debit of
     * {@code debit} (the source currency) and the clamp-checked credit of {@code credit} (the target currency)
     * commit together or not at all. Returns {@link TransferError#INSUFFICIENT_FUNDS} when the guarded debit
     * changed no rows and {@link TransferError#BALANCE_MAX_EXCEEDED} when the credit would push the target
     * balance past its {@code max-balance}: in either case nothing is mutated. This is the {@code /exchange}
     * seam: it mirrors {@link #transfer} but moves between two currencies of the same owner, so a conversion can
     * never debit one currency without crediting the other.
     */
    Result<Unit, TransferError> exchange(PlayerRef owner, Money debit, Money credit);

    /** The ranked rows for {@code currency}, descending by balance, bounded by {@code limit}; exempt owners excluded. */
    List<BaltopRow> top(Currency currency, int limit);
}
