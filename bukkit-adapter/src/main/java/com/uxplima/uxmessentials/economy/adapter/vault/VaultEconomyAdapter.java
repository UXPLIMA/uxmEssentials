package com.uxplima.uxmessentials.economy.adapter.vault;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.bukkit.OfflinePlayer;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Transaction;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges a legacy Vault economy into this plugin's {@link EconomyProvider} port, the compatibility bridge
 * for the large installed base of Vault-only economies ({@code docs/11-economy-integration.md} §2, §6). One
 * of only two classes (with {@code TreasuryEconomyAdapter}) allowed to import a provider SDK; the ArchUnit
 * fence {@code economyDomainHasNoProviderSdk} forbids {@code net.milkbowl.vault..} anywhere above this
 * adapter.
 *
 * <p>Vault's {@code Economy} API has no notion of multiple currencies, so this adapter is
 * <strong>single-currency</strong>: it exposes exactly the configured default {@link Currency} from
 * {@link #currencies()}, and any operation in another currency resolves {@code CURRENCY_UNSUPPORTED} (a
 * {@link TransferResult.DenyWith} for a transfer) rather than silently converting. There is no implicit
 * cross-currency conversion. Amounts cross the boundary as {@code double} because that is Vault's surface;
 * inside this plugin they are {@link Money}/{@link BigDecimal}, so the conversion lives here and only here.
 */
@NullMarked
public final class VaultEconomyAdapter implements EconomyProvider {

    private final Economy vault;
    private final Currency currency;
    private final Function<PlayerRef, OfflinePlayer> resolve;
    private final MessageKey unsupportedCurrency;

    public VaultEconomyAdapter(
            Economy vault,
            Currency defaultCurrency,
            Function<PlayerRef, OfflinePlayer> resolve,
            MessageKey unsupportedCurrency) {
        this.vault = Objects.requireNonNull(vault, "vault");
        this.currency = Objects.requireNonNull(defaultCurrency, "defaultCurrency");
        this.resolve = Objects.requireNonNull(resolve, "resolve");
        this.unsupportedCurrency = Objects.requireNonNull(unsupportedCurrency, "unsupportedCurrency");
    }

    @Override
    public boolean hasAccount(PlayerRef owner, Currency requested) {
        return serves(requested) && vault.hasAccount(player(owner));
    }

    @Override
    public void ensureAccount(PlayerRef owner, Currency requested) {
        if (serves(requested)) {
            vault.createPlayerAccount(player(owner));
        }
    }

    @Override
    public Money balance(PlayerRef owner, Currency requested) {
        if (!serves(requested)) {
            return Money.zero(requested);
        }
        return Money.of(currency, BigDecimal.valueOf(vault.getBalance(player(owner))));
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        if (!serves(amount.currency())) {
            return Result.err(TransferError.CURRENCY_UNSUPPORTED);
        }
        EconomyResponse response =
                vault.depositPlayer(player(owner), amount.amount().doubleValue());
        return response.transactionSuccess() ? Result.ok() : Result.err(TransferError.BALANCE_MAX_EXCEEDED);
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        if (!serves(amount.currency())) {
            return Result.err(TransferError.CURRENCY_UNSUPPORTED);
        }
        if (!vault.has(player(owner), amount.amount().doubleValue())) {
            return Result.err(TransferError.INSUFFICIENT_FUNDS);
        }
        EconomyResponse response =
                vault.withdrawPlayer(player(owner), amount.amount().doubleValue());
        return response.transactionSuccess() ? Result.ok() : Result.err(TransferError.INSUFFICIENT_FUNDS);
    }

    @Override
    public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to)) {
            return TransferResult.denyWith(unsupportedCurrency);
        }
        if (!serves(amount.currency())) {
            return TransferResult.denyWith(unsupportedCurrency);
        }
        if (debit(from, amount).isErr()) {
            return TransferResult.insufficientFunds(amount, balance(from, amount.currency()));
        }
        credit(to, amount);
        return TransferResult.allow(
                Transaction.debit(from, amount, balance(from, amount.currency()), java.time.Instant.now()),
                Transaction.credit(to, amount, balance(to, amount.currency()), java.time.Instant.now()));
    }

    @Override
    public List<BaltopRow> top(Currency requested, int limit) {
        // Vault exposes no account enumeration, so it cannot serve a server-wide leaderboard; baltop over a
        // Vault economy is empty rather than a partial or invented ranking.
        return List.of();
    }

    @Override
    public Set<Currency> currencies() {
        return Set.of(currency);
    }

    private boolean serves(Currency requested) {
        return currency.equals(Objects.requireNonNull(requested, "currency"));
    }

    private OfflinePlayer player(PlayerRef owner) {
        return resolve.apply(Objects.requireNonNull(owner, "owner"));
    }
}
