package com.uxplima.uxmessentials.economy.domain;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;

/**
 * The modelled failures a shared-bank operation ({@code /bank create|deposit|withdraw|addmember|removemember})
 * can produce. Each value is a distinct outcome the command adapter renders through its own
 * {@link EconomyMessageKey}. Never collapsed to a single "insufficient funds" notice, so a missing bank, a
 * permission denial, and an empty wallet are all told apart.
 *
 * <p>A shared bank is a native-ledger feature: a deposit/withdraw moves money through the wallet repository the
 * economy context owns. When the active provider is a foreign economy (Treasury/Vault), the atomic
 * wallet-and-bank move cannot be applied through that repository, so {@link #PROVIDER_UNSUPPORTED} guards the
 * money-moving operations rather than risk a non-atomic move.
 */
public enum BankError {

    /** A bank with the requested id already exists. */
    ID_TAKEN(EconomyMessageKey.BANK_ID_TAKEN),

    /** The named bank does not exist. */
    NOT_FOUND(EconomyMessageKey.BANK_NOT_FOUND),

    /** The actor lacks the role permission for the requested action. */
    NO_PERMISSION(EconomyMessageKey.BANK_NO_PERMISSION),

    /** The depositor cannot cover the amount: the guarded wallet debit changed no rows. */
    INSUFFICIENT_FUNDS(EconomyMessageKey.BANK_INSUFFICIENT_FUNDS),

    /** The bank cannot cover the withdrawal: the guarded balance update changed no rows. */
    INSUFFICIENT_BANK_FUNDS(EconomyMessageKey.BANK_INSUFFICIENT_BANK_FUNDS),

    /** The credit of a withdrawal would push the player past the currency's max balance. */
    BALANCE_MAX_EXCEEDED(EconomyMessageKey.BALANCE_MAX_EXCEEDED),

    /** The target is already a member of the bank. */
    ALREADY_MEMBER(EconomyMessageKey.BANK_ALREADY_MEMBER),

    /** The target is not a member of the bank. */
    NOT_MEMBER(EconomyMessageKey.BANK_NOT_MEMBER),

    /** The target is the bank leader and cannot be removed this way. */
    CANNOT_REMOVE_LEADER(EconomyMessageKey.BANK_CANNOT_REMOVE_LEADER),

    /** The active economy provider is foreign, so the native-ledger bank move cannot be applied atomically. */
    PROVIDER_UNSUPPORTED(EconomyMessageKey.BANK_PROVIDER_UNSUPPORTED);

    private final EconomyMessageKey messageKey;

    BankError(EconomyMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public EconomyMessageKey messageKey() {
        return messageKey;
    }
}
