package com.uxplima.uxmessentials.persistence.runtime;

import java.util.Objects;
import java.util.function.Function;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jspecify.annotations.NullMarked;

/**
 * A small transaction helper over a jOOQ {@link DSLContext}.
 *
 * <p>It runs a unit of work inside a single jOOQ transaction: the work commits if it returns normally
 * and rolls back if it throws. This is the seam the offline-row upsert relies on, the parent
 * {@code ensureUserExists} insert and the dependent write happen in the same transaction, so a foreign
 * key can never observe a missing parent ({@code docs/01-architecture.md} §8 persistence invariants).
 * jOOQ's {@code DataAccessException} is translated to the layer's {@link PersistenceException} so callers
 * never catch a jOOQ type. Transactions are expected to be millisecond-scale and must never wrap a
 * Bukkit API call ({@code docs/02-concurrency.md} §7.3).
 */
@NullMarked
public final class Transactions {

    private Transactions() {}

    /**
     * Run {@code work} in a transaction and return its result, committing on success and rolling back on
     * any exception. The transactional {@link DSLContext} passed to {@code work} must be the one used for
     * every statement inside the unit of work.
     */
    public static <T> T inTransaction(DSLContext dsl, Function<DSLContext, T> work) {
        Objects.requireNonNull(dsl, "dsl");
        Objects.requireNonNull(work, "work");
        try {
            return dsl.transactionResult(configuration -> work.apply(configuration.dsl()));
        } catch (DataAccessException cause) {
            throw new PersistenceException("transaction failed and was rolled back", cause);
        }
    }
}
