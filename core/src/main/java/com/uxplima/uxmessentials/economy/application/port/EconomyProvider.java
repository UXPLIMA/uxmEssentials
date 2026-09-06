package com.uxplima.uxmessentials.economy.application.port;

import java.util.List;
import java.util.Set;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The single outbound port that abstracts "the server's economy" for every context that touches money. The
 * native ledger implements it natively; the Treasury and Vault outbound adapters bridge a foreign economy
 * into it. Every {@code /balance} / {@code /pay} / {@code /baltop} / eco-admin command and every
 * cross-context charge ({@code warps} {@code WarpCost}, a kit purchase) routes through these methods and
 * nothing else. There is no escape hatch into {@code economy.domain.*} (the economy GLOSSARY,
 * {@code docs/11-economy-integration.md} §1).
 *
 * <p>The contract is small on purpose and always carries a {@link Currency}, so a single-currency provider
 * (legacy Vault) is the degenerate case of a multi-currency one, not a different shape. The four behavioural
 * properties every implementation must satisfy. Atomic pay, insufficient-funds rejection, descending
 * baltop ordering, and concurrent-debit double-spend safety. Are pinned once by the abstract
 * {@code EconomyProviderContractTest} (§7); a provider that fails any of them is unfit to register through
 * the {@code ServicesManager}.
 *
 * <p>The economy {@code domain}/{@code application} layers (this port included) are ArchUnit-fenced from
 * importing any Vault ({@code net.milkbowl.vault..}) or Treasury ({@code me.lokka30.treasury..}) type, the
 * port is the only seam, and the SDK types live solely in the outbound adapters
 * ({@code economyDomainHasNoProviderSdk}).
 */
public interface EconomyProvider {

    /** True when {@code owner} already has a wallet row in {@code currency}. */
    boolean hasAccount(PlayerRef owner, Currency currency);

    /**
     * Materialise {@code owner}'s account so a subsequent write cannot fail on a missing row, the
     * offline-target {@code ensureUserExists} upsert ({@code docs/11-economy-integration.md} §10.2),
     * idempotent and cheap. Credits the currency's starting balance only on first creation.
     */
    void ensureAccount(PlayerRef owner, Currency currency);

    /** {@code owner}'s balance in {@code currency}, or {@code zero} when they have no row in it. */
    Money balance(PlayerRef owner, Currency currency);

    /**
     * Single-sided credit (an admin give, the {@code /pay} credit leg). Honours the currency's
     * {@code max-balance} clamp, rejecting rather than clamping.
     */
    Result<Unit, TransferError> credit(PlayerRef owner, Money amount);

    /**
     * Guarded single-sided debit; rejects with {@link TransferError#INSUFFICIENT_FUNDS} rather than going
     * below the currency's minimum. The guard is at the database (the guarded {@code UPDATE}), not in the
     * JVM, so two concurrent debits never both succeed past zero (§3).
     */
    Result<Unit, TransferError> debit(PlayerRef owner, Money amount);

    /**
     * Atomic two-sided move, the {@code /pay} and {@code WarpCost} path. Never partially applies: both legs
     * commit together or neither does. Returns a {@link TransferResult} so a policy refusal ({@code DenyWith})
     * and a funds refusal ({@code InsufficientFunds}) are distinct first-class outcomes alongside
     * {@code Allow}.
     */
    TransferResult transfer(PlayerRef from, PlayerRef to, Money amount);

    /**
     * The ranked read-model for {@code currency}, descending by balance and bounded by {@code limit}, the
     * {@code /baltop} seam. Exempt owners are excluded where the snapshot is built. Ordering is a tested
     * property of the contract suite.
     */
    List<BaltopRow> top(Currency currency, int limit);

    /** The currencies this provider can serve: the full configured set, or a single element for Vault. */
    Set<Currency> currencies();
}
