package com.uxplima.uxmessentials.economy.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.event.EconomyEvent;
import com.uxplima.uxmessentials.economy.domain.event.WalletCredited;
import com.uxplima.uxmessentials.economy.domain.event.WalletDebited;
import com.uxplima.uxmessentials.economy.domain.event.WalletRejected;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * The aggregate root over one player's balances, a {@link Money} <strong>per {@link Currency}</strong>,
 * never one scalar (the economy GLOSSARY, {@code docs/11-economy-integration.md} §6.1). It owns the
 * invariants nothing else in the plugin enforces: a balance never drops below the currency's minimum (a
 * debit short of funds is rejected, not clamped), never rises above its maximum (a credit that would breach
 * it is rejected, not clamped), and currency arithmetic never crosses currencies. Every mutation is a pure
 * transition returning a new {@code Wallet} plus the {@link EconomyEvent} it raised, the aggregate never
 * reaches for a repository, a clock, or a provider; the application layer loads the wallet, applies a
 * transition, persists the result, and publishes the event.
 *
 * <p>The map shape is what makes the whole port multi-currency-ready without a second design: a fresh
 * install ships one default currency, operators add more in config, and the aggregate, the persistence key
 * {@code (uuid, currency)}, and the {@code EconomyProvider} carry them with zero code change because the
 * balance was a map from day one. {@link #balanceOf(Currency)} is a map lookup returning {@code zero} for a
 * currency the owner has never touched, so the {@code (uuid, currency)} rows stay sparse.
 */
public final class Wallet {

    private final PlayerRef owner;
    private final Map<Currency, Money> balances;

    private Wallet(PlayerRef owner, Map<Currency, Money> balances) {
        this.owner = owner;
        this.balances = balances;
    }

    /** A wallet with no balances yet: every currency projects to {@code zero}. */
    public static Wallet empty(PlayerRef owner) {
        return new Wallet(Objects.requireNonNull(owner, "owner"), new LinkedHashMap<>());
    }

    /** A wallet rebuilt from its persisted per-currency balances. */
    public static Wallet of(PlayerRef owner, Map<Currency, Money> balances) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(balances, "balances");
        Map<Currency, Money> copy = new LinkedHashMap<>();
        for (Map.Entry<Currency, Money> entry : balances.entrySet()) {
            requireMatching(entry.getKey(), entry.getValue());
            copy.put(entry.getKey(), entry.getValue());
        }
        return new Wallet(owner, copy);
    }

    /** The player these balances belong to. */
    public PlayerRef owner() {
        return owner;
    }

    /** The owner's balance in {@code currency}, or {@code zero} for a currency they have never held. */
    public Money balanceOf(Currency currency) {
        Objects.requireNonNull(currency, "currency");
        return balances.getOrDefault(currency, Money.zero(currency));
    }

    /** A read-only view of every currency the owner currently holds a balance in. */
    public Map<Currency, Money> balances() {
        return Map.copyOf(balances);
    }

    /**
     * Credit {@code amount} into the matching currency. Rejected (not clamped) when the result would exceed
     * the currency's {@code max-balance}; otherwise raises {@link WalletCredited} carrying the minted
     * {@link Transaction}. A rejection carries both the {@link EconomyError} and the {@link WalletRejected}
     * the aggregate minted, so the caller renders the one and publishes the other.
     */
    public Result<Change, Rejection> credit(Money amount, Instant now) {
        Money have = requireAmount(amount, now);
        Money resulting = have.plus(amount);
        if (resulting.amount().compareTo(amount.currency().max()) > 0) {
            return Result.err(rejected(amount, have, EconomyError.BALANCE_MAX_EXCEEDED, now));
        }
        Transaction tx = Transaction.credit(owner, amount, resulting, now);
        return Result.ok(applied(amount.currency(), resulting, new WalletCredited(owner, amount, resulting, tx, now)));
    }

    /**
     * Debit {@code amount} from the matching currency. Rejected (not clamped) when the owner cannot cover it
     * without dropping below the currency's {@code min-balance}; otherwise raises {@link WalletDebited}
     * carrying the minted {@link Transaction}. This is the in-aggregate guard; the load-bearing concurrent
     * guard for the {@code /pay} path lives at the database (the guarded {@code UPDATE}, §3).
     */
    public Result<Change, Rejection> debit(Money amount, Instant now) {
        Money have = requireAmount(amount, now);
        Money resulting = have.minus(amount);
        if (resulting.amount().compareTo(amount.currency().min()) < 0) {
            return Result.err(rejected(amount, have, EconomyError.INSUFFICIENT_FUNDS, now));
        }
        Transaction tx = Transaction.debit(owner, amount, resulting, now);
        return Result.ok(applied(amount.currency(), resulting, new WalletDebited(owner, amount, resulting, tx, now)));
    }

    private Money requireAmount(Money amount, Instant now) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(now, "now");
        if (amount.isNegative()) {
            throw new IllegalArgumentException("wallet change amount must not be negative: " + amount.amount());
        }
        return balanceOf(amount.currency());
    }

    private Change applied(Currency currency, Money resulting, EconomyEvent event) {
        Map<Currency, Money> next = new LinkedHashMap<>(balances);
        next.put(currency, resulting);
        return Change.applied(new Wallet(owner, next), event);
    }

    private Rejection rejected(Money amount, Money have, EconomyError error, Instant now) {
        return new Rejection(error, new WalletRejected(owner, amount, have, error, now));
    }

    private static void requireMatching(Currency currency, Money money) {
        if (!currency.equals(money.currency())) {
            throw new IllegalArgumentException(
                    "balance currency mismatch: keyed " + currency + " holds " + money.currency());
        }
    }

    /**
     * The outcome of an applied mutation: the resulting wallet and the event it raised. The application
     * layer persists the new balance and publishes the event. A refused mutation never produces a
     * {@code Change}. It returns {@code Result.err} carrying the {@link WalletRejected} as a value the
     * caller publishes itself, so a refusal is still observed exactly once.
     *
     * @param result the wallet after the mutation
     * @param event the event to publish for the applied change
     */
    public record Change(Wallet result, EconomyEvent event) {

        public Change {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(event, "event");
        }

        static Change applied(Wallet result, EconomyEvent event) {
            return new Change(result, event);
        }
    }
}
