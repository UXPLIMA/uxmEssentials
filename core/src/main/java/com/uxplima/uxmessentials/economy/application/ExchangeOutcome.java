package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.economy.domain.TransferError;
import org.jspecify.annotations.Nullable;

/**
 * Result of a currency exchange request.
 */
public record ExchangeOutcome(
        Status status,
        BigDecimal sourceAmount,
        BigDecimal targetAmount,
        @Nullable TransferError error) {
    public enum Status {
        SUCCESS,
        RATE_NOT_FOUND,
        INSUFFICIENT_FUNDS,
        LIMIT_EXCEEDED,
        FAILED,

        /** One side of the conversion is a currency configured with {@code exchange-allowed = false}. */
        CURRENCY_DISABLED,

        /**
         * The active economy provider is foreign (Treasury/Vault), so the atomic two-currency move cannot be
         * applied through the native ledger and the feature is refused without moving anything, the same
         * gating loans and banks use.
         */
        PROVIDER_UNSUPPORTED
    }

    public static ExchangeOutcome success(BigDecimal sourceAmount, BigDecimal targetAmount) {
        return new ExchangeOutcome(Status.SUCCESS, sourceAmount, targetAmount, null);
    }

    public static ExchangeOutcome rateNotFound() {
        return new ExchangeOutcome(Status.RATE_NOT_FOUND, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    public static ExchangeOutcome insufficientFunds() {
        return new ExchangeOutcome(
                Status.INSUFFICIENT_FUNDS, BigDecimal.ZERO, BigDecimal.ZERO, TransferError.INSUFFICIENT_FUNDS);
    }

    public static ExchangeOutcome limitExceeded() {
        return new ExchangeOutcome(
                Status.LIMIT_EXCEEDED, BigDecimal.ZERO, BigDecimal.ZERO, TransferError.BALANCE_MAX_EXCEEDED);
    }

    public static ExchangeOutcome failed(TransferError error) {
        return new ExchangeOutcome(Status.FAILED, BigDecimal.ZERO, BigDecimal.ZERO, error);
    }

    /** One side of the conversion has {@code exchange-allowed = false}; nothing was moved. */
    public static ExchangeOutcome currencyDisabled() {
        return new ExchangeOutcome(Status.CURRENCY_DISABLED, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    /** The active provider is foreign, so the native-ledger exchange cannot run; nothing was moved. */
    public static ExchangeOutcome providerUnsupported() {
        return new ExchangeOutcome(Status.PROVIDER_UNSUPPORTED, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }
}
