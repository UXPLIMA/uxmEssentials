package com.uxplima.uxmessentials.economy.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Precision;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The plugin's own ledger, expressed as a {@link CurrencyBackend}. It adds nothing to
 * {@link WalletRepository} and takes nothing away: the debit is still the repository's guarded
 * {@code UPDATE ... WHERE amount >= ?}, so it is the one backend that can honestly answer
 * {@link #atomicDebit()} with {@code true}. Balances are rows, so an offline owner is writable.
 */
public final class NativeCurrencyBackend implements CurrencyBackend {

    /** The reserved backend id a currency gets when it names none. */
    public static final String ID = "native";

    private final WalletRepository repo;

    public NativeCurrencyBackend(WalletRepository repo) {
        this.repo = Objects.requireNonNull(repo, "repo");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean worksOffline() {
        return true;
    }

    @Override
    public boolean atomicDebit() {
        return true;
    }

    @Override
    public Precision precision() {
        return Precision.DECIMAL;
    }

    @Override
    public Money balance(PlayerRef owner, Currency currency) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currency, "currency");
        return repo.findByOwner(owner).map(wallet -> wallet.balanceOf(currency)).orElse(Money.zero(currency));
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        repo.ensureOwner(owner);
        return repo.credit(owner, amount);
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        return repo.debit(owner, amount);
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        Objects.requireNonNull(currency, "currency");
        if (limit <= 0) {
            throw new IllegalArgumentException("baltop limit must be positive: " + limit);
        }
        return repo.top(currency, limit);
    }

    /**
     * Materialise the owner's ledger row, crediting the currency's starting balance only on first creation;
     * idempotent. The seam {@link RoutingEconomyProvider} uses to honour {@code ensureAccount} for a native
     * currency without minting a transaction, which a plain {@code credit} of zero would.
     */
    void ensureOwner(PlayerRef owner) {
        repo.ensureOwner(Objects.requireNonNull(owner, "owner"));
    }

    /**
     * The repository's atomic two-sided move. The guarded debit of {@code from} and the credit of {@code to}
     * commit together or not at all. This is the guarantee routing must not lose, so it is exposed only to
     * {@link RoutingEconomyProvider}, which uses it in place of a debit-then-credit pair whenever a currency
     * lives on the native ledger.
     */
    Result<Unit, TransferError> transferAtomically(PlayerRef from, PlayerRef to, Money amount) {
        repo.ensureOwner(to);
        return repo.transfer(from, to, amount);
    }
}
