package com.uxplima.uxmessentials.persistence.runtime;

import java.util.Objects;
import java.util.function.Function;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jspecify.annotations.NullMarked;

/**
 * Base class for a context's outbound persistence adapter (the {@code homes} {@code HomeRepository},
 * the {@code warps} {@code WarpRepository}, …).
 *
 * <p>It holds the shared {@link DSLContext} and offers two protected seams: {@link #read} for a plain
 * query that borrows a pooled connection, and {@link #write} for a unit of work that must commit or roll
 * back atomically. Both translate jOOQ's {@code DataAccessException} into the layer's
 * {@link PersistenceException} so a subclass never leaks a jOOQ exception type past its port. The base is
 * intentionally thin: it carries no caching (that is the cache decorator's job) and no table knowledge.
 */
@NullMarked
public abstract class JooqRepository {

    private final DSLContext dsl;

    protected JooqRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    /** The shared context, for a subclass that needs the raw DSL (e.g. to build a typed query). */
    protected final DSLContext dsl() {
        return dsl;
    }

    /** Run a read-only query, wrapping any jOOQ failure as a {@link PersistenceException}. */
    protected final <T> T read(Function<DSLContext, T> query) {
        Objects.requireNonNull(query, "query");
        try {
            return query.apply(dsl);
        } catch (DataAccessException cause) {
            throw new PersistenceException("query failed", cause);
        }
    }

    /**
     * Run a mutating unit of work in a transaction (commit on success, roll back on throw). The
     * transactional {@link DSLContext} handed to {@code work} is the one to use for every statement so
     * an offline-row upsert and the dependent write share the same transaction.
     */
    protected final <T> T write(Function<DSLContext, T> work) {
        Objects.requireNonNull(work, "work");
        return Transactions.inTransaction(dsl, work);
    }
}
