package com.uxplima.uxmessentials.economy.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Transaction;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A debit applied to a wallet, the debit leg of a {@code /pay}, a {@code WarpCost} charge, an admin take.
 * Carries the {@link Transaction} the aggregate minted and the resulting per-currency balance. Maps to one
 * {@code event=economy_debit} audit line. A transfer that resolves to an applied move emits exactly one
 * {@code WalletDebited} and one {@link WalletCredited}.
 *
 * @param owner the wallet that was debited
 * @param amount the {@link Money} removed, carrying its currency
 * @param resulting the owner's balance in that currency after the debit
 * @param transaction the change the aggregate minted for this debit
 * @param occurredAt when the debit applied
 */
public record WalletDebited(PlayerRef owner, Money amount, Money resulting, Transaction transaction, Instant occurredAt)
        implements EconomyEvent {

    public WalletDebited {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(resulting, "resulting");
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
