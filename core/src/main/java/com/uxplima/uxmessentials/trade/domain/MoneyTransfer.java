package com.uxplima.uxmessentials.trade.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One leg of a trade's money settlement: the {@code payer} side owes {@code amount} of {@code currencyId} to the other
 * side. A {@code MoneyTransfer} is derived purely from the two {@link TradeOffer}s a session holds, one leg per
 * non-zero money entry a side has staked, so the settlement decision stays testable without an economy provider. The
 * recipient is always {@code payer.other()}; naming only the payer keeps the value minimal.
 *
 * @param payer the side that staked, and therefore pays, this money
 * @param currencyId the currency id the amount is denominated in (an operator {@code currencies-allowed} entry)
 * @param amount the strictly-positive figure to move from the payer to the other side
 */
public record MoneyTransfer(TradeSide payer, String currencyId, BigDecimal amount) {

    public MoneyTransfer {
        Objects.requireNonNull(payer, "payer");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(amount, "amount");
        if (currencyId.isBlank()) {
            throw new IllegalArgumentException("currencyId must not be blank");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("transfer amount must be strictly positive: " + amount);
        }
    }

    /** The side that receives this money, always the opposite of the payer. */
    public TradeSide recipient() {
        return payer.other();
    }
}
