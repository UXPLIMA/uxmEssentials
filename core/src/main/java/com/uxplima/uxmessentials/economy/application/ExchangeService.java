package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.ExchangeRate;
import com.uxplima.uxmessentials.economy.domain.ExchangeRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Orchestrates a player's currency conversions. The money move is a single atomic call into the
 * {@link WalletRepository}. The guarded debit of the source currency and the clamp-checked credit of the
 * target currency commit together or not at all, so a conversion can never debit one currency without
 * crediting the other, exactly as a {@code /pay} can never debit a sender without crediting a receiver.
 *
 * <p>Exchange is a native-ledger feature: the atomic two-currency move runs through the wallet repository the
 * economy context owns. When the active provider is a foreign economy (Treasury/Vault) the move cannot be
 * applied atomically through that repository, so the feature is refused with
 * {@link ExchangeOutcome#providerUnsupported()} rather than attempting a non-atomic debit-then-credit that
 * could lose money, mirroring {@link LoanService} and {@link BankService}.
 */
public final class ExchangeService {

    private final WalletRepository walletRepository;
    private final ExchangeRegistry exchangeRegistry;
    private final boolean nativeLedger;

    public ExchangeService(WalletRepository walletRepository, ExchangeRegistry exchangeRegistry, boolean nativeLedger) {
        this.walletRepository = Objects.requireNonNull(walletRepository, "walletRepository");
        this.exchangeRegistry = Objects.requireNonNull(exchangeRegistry, "exchangeRegistry");
        this.nativeLedger = nativeLedger;
    }

    public ExchangeRegistry registry() {
        return exchangeRegistry;
    }

    /**
     * Converts {@code sourceAmount} of {@code sourceCurrency} into {@code targetCurrency} for {@code player} in a
     * single atomic transaction. Refuses without moving anything when the active provider is foreign, no rate is
     * configured, the amount is non-positive, the target rounds to zero, the source is short, or the target
     * would exceed its max balance.
     */
    public ExchangeOutcome exchange(
            PlayerRef player, BigDecimal sourceAmount, Currency sourceCurrency, Currency targetCurrency) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(sourceAmount, "sourceAmount");
        Objects.requireNonNull(sourceCurrency, "sourceCurrency");
        Objects.requireNonNull(targetCurrency, "targetCurrency");

        if (!nativeLedger) {
            return ExchangeOutcome.providerUnsupported();
        }
        if (!sourceCurrency.exchangeAllowed() || !targetCurrency.exchangeAllowed()) {
            return ExchangeOutcome.currencyDisabled();
        }
        if (sourceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ExchangeOutcome.failed(TransferError.BELOW_MINIMUM);
        }

        Optional<ExchangeRate> rateOpt = exchangeRegistry.findRate(sourceCurrency.id(), targetCurrency.id());
        if (rateOpt.isEmpty()) {
            return ExchangeOutcome.rateNotFound();
        }

        ExchangeRate rate = rateOpt.get();
        Optional<BigDecimal> targetOpt = rate.calculateTargetAmount(sourceAmount, targetCurrency.precision());
        if (targetOpt.isEmpty()) {
            // The source is so small the target rounds to zero: refuse rather than debit for nothing.
            return ExchangeOutcome.failed(TransferError.BELOW_MINIMUM);
        }
        BigDecimal targetAmount = targetOpt.get();

        Money debit = Money.of(sourceCurrency, sourceAmount);
        Money credit = Money.of(targetCurrency, targetAmount);
        Result<Unit, TransferError> moved = walletRepository.exchange(player, debit, credit);
        if (moved.isErr()) {
            TransferError err = moved.errorOrThrow();
            return switch (err) {
                case INSUFFICIENT_FUNDS -> ExchangeOutcome.insufficientFunds();
                case BALANCE_MAX_EXCEEDED -> ExchangeOutcome.limitExceeded();
                default -> ExchangeOutcome.failed(err);
            };
        }

        return ExchangeOutcome.success(sourceAmount, targetAmount);
    }
}
