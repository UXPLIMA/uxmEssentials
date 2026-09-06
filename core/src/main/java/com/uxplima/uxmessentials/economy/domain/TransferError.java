package com.uxplima.uxmessentials.economy.domain;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;

/**
 * The modelled failures an atomic two-sided move (a {@code /pay}, a {@code WarpCost} charge) can produce.
 * Distinct from {@link EconomyError} because a transfer has policy gates a single-sided credit/debit does
 * not. Paying yourself, paying below the currency minimum, paying a target who has disabled incoming pay,
 * or naming a currency the active provider cannot serve. Each value carries the {@link EconomyMessageKey}
 * the command adapter renders.
 *
 * <p>This is the error half of the {@code EconomyProvider.transfer} contract: a transfer either applies both
 * legs atomically or returns one of these and mutates nothing. Rejected, not partially applied (the
 * double-spend invariant, {@code docs/02-concurrency.md} §6.7).
 */
public enum TransferError {

    /** The sender cannot cover the amount; the guarded debit changed no rows (the contract's rejection case). */
    INSUFFICIENT_FUNDS(EconomyMessageKey.PAY_INSUFFICIENT),

    /** A {@code /pay} to the payer's own account, refused before any leg runs. */
    SELF_TRANSFER(EconomyMessageKey.PAY_SELF),

    /** The amount is below the currency's configured {@code min-pay}. */
    BELOW_MINIMUM(EconomyMessageKey.PAY_BELOW_MINIMUM),

    /** The target has turned off incoming pay via {@code /paytoggle} (honoured before the debit). */
    TARGET_DISABLED(EconomyMessageKey.PAY_TARGET_DISABLED),

    /** A credit leg that would push the recipient past the currency's {@code max-balance}. */
    BALANCE_MAX_EXCEEDED(EconomyMessageKey.BALANCE_MAX_EXCEEDED),

    /** The named currency exists in config but the active provider cannot hold it (e.g. legacy Vault). */
    CURRENCY_UNSUPPORTED(EconomyMessageKey.CURRENCY_UNSUPPORTED),

    /** The currency is configured with {@code transfer-allowed = false}, so it cannot move between players. */
    CURRENCY_TRANSFER_DISABLED(EconomyMessageKey.PAY_CURRENCY_DISABLED),

    /** The recipient's inventory is full and the physical items cannot be credited. */
    PHYSICAL_INVENTORY_FULL(EconomyMessageKey.PHYSICAL_INVENTORY_FULL),

    /** The target player is offline and a physical transaction requires them to be online. */
    PLAYER_OFFLINE(EconomyMessageKey.PHYSICAL_PLAYER_OFFLINE);

    private final EconomyMessageKey messageKey;

    TransferError(EconomyMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public EconomyMessageKey messageKey() {
        return messageKey;
    }
}
