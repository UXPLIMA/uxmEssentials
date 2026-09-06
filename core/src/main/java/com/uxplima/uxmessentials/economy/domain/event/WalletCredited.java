package com.uxplima.uxmessentials.economy.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Transaction;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A credit applied to a wallet, an admin give, the credit leg of a {@code /pay}, a starting-balance credit.
 * Carries the {@link Transaction} the aggregate minted (so the ledger row and the event agree on identity)
 * and the resulting per-currency balance. Maps to one {@code event=economy_credit} audit line.
 *
 * @param owner the wallet that was credited
 * @param amount the {@link Money} added, carrying its currency
 * @param resulting the owner's balance in that currency after the credit
 * @param transaction the change the aggregate minted for this credit
 * @param occurredAt when the credit applied
 */
public record WalletCredited(
        PlayerRef owner, Money amount, Money resulting, Transaction transaction, Instant occurredAt)
        implements EconomyEvent {

    public WalletCredited {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(resulting, "resulting");
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
