package com.uxplima.uxmessentials.economy.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * The identity of a {@link Transaction}. A {@code Transaction} is an entity. It has identity, not just
 * value, so it carries this id, minted by the aggregate when it applies a change, and the persisted ledger
 * row records it as the durable reference for a forensic trail.
 *
 * @param value the stable transaction identifier
 */
public record TransactionId(UUID value) {

    public TransactionId {
        Objects.requireNonNull(value, "value");
    }

    /** A fresh, random transaction id, minted by the aggregate, never by the caller. */
    public static TransactionId random() {
        return new TransactionId(UUID.randomUUID());
    }
}
