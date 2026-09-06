package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Precision;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.EconomyQuery;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.Hooks;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The server economy as a currency backend, reached through the already-resolved {@link EconomyQuery} hook. No
 * provider SDK type appears here. The hook is the seam, and its absent default already no-ops when no Vault economy
 * is registered, which this backend reports as {@link TransferError#CURRENCY_UNSUPPORTED}.
 *
 * <p>Vault's economy exposes read and take as separate calls, so a debit reads then withdraws and cannot be a
 * guarded compare-and-take. {@link #atomicDebit()} is therefore false and {@code SerialisingCurrencyBackend} wraps
 * this backend so two threads cannot both observe the pre-debit balance and both succeed.
 */
public final class VaultCurrencyBackend implements CurrencyBackend {

    private final EconomyQuery economy;

    public VaultCurrencyBackend(Hooks hooks) {
        this.economy = Objects.requireNonNull(hooks, "hooks").capability(EconomyQuery.class);
    }

    @Override
    public String id() {
        return "vault";
    }

    @Override
    public boolean available() {
        return economy.available();
    }

    @Override
    public boolean worksOffline() {
        return true;
    }

    @Override
    public boolean atomicDebit() {
        return false;
    }

    @Override
    public Precision precision() {
        return Precision.DECIMAL;
    }

    @Override
    public Money balance(PlayerRef owner, Currency currency) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currency, "currency");
        return available()
                ? Money.of(currency, BigDecimal.valueOf(economy.balance(owner.uuid())))
                : Money.zero(currency);
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        if (!available()) {
            return Result.err(TransferError.CURRENCY_UNSUPPORTED);
        }
        boolean deposited = economy.deposit(owner.uuid(), amount.amount().doubleValue());
        return deposited ? Result.ok() : Result.err(TransferError.CURRENCY_UNSUPPORTED);
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        if (!available()) {
            return Result.err(TransferError.CURRENCY_UNSUPPORTED);
        }
        double value = amount.amount().doubleValue();
        if (!economy.has(owner.uuid(), value)) {
            return Result.err(TransferError.INSUFFICIENT_FUNDS);
        }
        return economy.withdraw(owner.uuid(), value) ? Result.ok() : Result.err(TransferError.INSUFFICIENT_FUNDS);
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        Objects.requireNonNull(currency, "currency");
        return List.of();
    }
}
