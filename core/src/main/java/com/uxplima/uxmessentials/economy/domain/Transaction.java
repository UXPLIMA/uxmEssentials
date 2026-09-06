package com.uxplima.uxmessentials.economy.domain;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A single credit or debit applied to a {@link Wallet}, the unit of change inside the aggregate, the
 * domain fact a persisted ledger row records. Entity (it has a {@link TransactionId}), but it lives
 * <em>inside</em> the Wallet aggregate and is never published or referenced from outside it; the
 * {@code WalletCredited}/{@code WalletDebited} events are what cross the aggregate boundary. The aggregate,
 * not the caller, mints a {@code Transaction} when it applies a change (the economy GLOSSARY).
 *
 * @param id the minted identity of this change
 * @param owner the wallet this change was applied to
 * @param kind whether value entered ({@link Kind#CREDIT}) or left ({@link Kind#DEBIT}) the wallet
 * @param amount the {@link Money} moved, carrying its currency
 * @param resulting the wallet's balance in that currency immediately after the change
 * @param at when the change was applied
 */
public record Transaction(TransactionId id, PlayerRef owner, Kind kind, Money amount, Money resulting, Instant at) {

    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(resulting, "resulting");
        Objects.requireNonNull(at, "at");
        if (!amount.currency().equals(resulting.currency())) {
            throw new IllegalArgumentException("transaction amount and resulting balance must share a currency");
        }
    }

    /** A credit entry: {@code amount} entered {@code owner}'s wallet, leaving {@code resulting} behind. */
    public static Transaction credit(PlayerRef owner, Money amount, Money resulting, Instant at) {
        return new Transaction(TransactionId.random(), owner, Kind.CREDIT, amount, resulting, at);
    }

    /** A debit entry: {@code amount} left {@code owner}'s wallet, leaving {@code resulting} behind. */
    public static Transaction debit(PlayerRef owner, Money amount, Money resulting, Instant at) {
        return new Transaction(TransactionId.random(), owner, Kind.DEBIT, amount, resulting, at);
    }

    /** The direction of a {@link Transaction}: value entering or leaving the wallet. */
    public enum Kind {
        CREDIT,
        DEBIT
    }
}
