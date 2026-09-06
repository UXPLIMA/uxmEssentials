package com.uxplima.uxmessentials.economy.domain;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;

/**
 * The modelled failures a loan operation ({@code /loan take}, {@code /loan pay}) can produce. Each value is a
 * distinct outcome the command adapter renders through its own {@link EconomyMessageKey}: never collapsed to
 * a single "insufficient funds" notice, so a debtor over their credit limit and a debtor with an empty wallet
 * see different messages.
 *
 * <p>A loan is a native-ledger feature: it moves money through the wallet repository the economy context owns.
 * When the active provider is a foreign economy (Treasury/Vault) the atomic disburse/repay cannot be performed
 * through that repository, so {@link #PROVIDER_UNSUPPORTED} guards the feature rather than risk a non-atomic
 * move that could create or lose money.
 */
public enum LoanError {

    /** The requested principal exceeds the debtor's credit-score-derived loan limit. */
    LIMIT_EXCEEDED(EconomyMessageKey.LOAN_LIMIT_EXCEEDED),

    /** The named loan does not exist (or has already been paid off). */
    NOT_FOUND(EconomyMessageKey.LOAN_NOT_FOUND),

    /** The caller is not the debtor of the named loan. */
    WRONG_DEBTOR(EconomyMessageKey.LOAN_WRONG_DEBTOR),

    /** The debtor cannot cover the installment: the guarded debit changed no rows. */
    INSUFFICIENT_FUNDS(EconomyMessageKey.LOAN_INSUFFICIENT_FUNDS),

    /** The requested principal or installment count is outside the allowed range. */
    INVALID_TERMS(EconomyMessageKey.LOAN_INVALID_TERMS),

    /** The active economy provider is foreign, so the native-ledger loan move cannot be applied atomically. */
    PROVIDER_UNSUPPORTED(EconomyMessageKey.LOAN_PROVIDER_UNSUPPORTED);

    private final EconomyMessageKey messageKey;

    LoanError(EconomyMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public EconomyMessageKey messageKey() {
        return messageKey;
    }
}
