package com.uxplima.uxmessentials.economy.domain;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;

/**
 * The modelled failures a single-sided wallet operation (a credit or a debit) can produce. Each value
 * carries the {@link EconomyMessageKey} the command adapter renders, so a use case returns a
 * {@code Result.err(EconomyError.X)} and the caller never re-derives the message, the failure reason and
 * its localized text never drift apart, mirroring {@code HomeError}/{@code WarpError}.
 *
 * <p>A debit short of funds is {@link #INSUFFICIENT_FUNDS} (rejected, never clamped); a credit that would
 * push a balance past the currency's configured maximum is {@link #BALANCE_MAX_EXCEEDED} (a hard guard, not
 * a silent clamp). Both are first-class values the {@link Wallet} aggregate produces, never thrown.
 */
public enum EconomyError {

    /** A debit larger than the balance, or one that would drive it below the currency's minimum. */
    INSUFFICIENT_FUNDS(EconomyMessageKey.PAY_INSUFFICIENT),

    /** A credit that would push the balance above the currency's configured {@code max-balance}. */
    BALANCE_MAX_EXCEEDED(EconomyMessageKey.BALANCE_MAX_EXCEEDED);

    private final EconomyMessageKey messageKey;

    EconomyError(EconomyMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public EconomyMessageKey messageKey() {
        return messageKey;
    }
}
