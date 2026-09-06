package com.uxplima.uxmessentials.persistence.runtime;

import java.util.Objects;

import javax.sql.DataSource;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the jOOQ {@link DSLContext} over a {@link DataSource} in the backend's dialect.
 *
 * <p>One {@code DSLContext} is created per enable and shared by every repository. It is thread-safe and
 * holds no connection itself; each operation borrows a pooled connection and returns it. The dialect is
 * the only backend-specific input, so the generated DSL renders correctly on SQLite, MySQL and
 * PostgreSQL from the same call sites. The provider knows nothing about specific tables; the generated
 * {@code Tables} constants and the repositories carry that.
 */
@NullMarked
public final class DslContextProvider {

    private DslContextProvider() {}

    /** Create a shared {@link DSLContext} bound to {@code dataSource}, rendering in {@code dialect}. */
    public static DSLContext create(DataSource dataSource, SQLDialect dialect) {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(dialect, "dialect");
        silenceJooqBanner();
        return DSL.using(dataSource, dialect);
    }

    /**
     * Suppress jOOQ's startup ASCII logo and "tip of the day" lines, which it prints to the server console
     * the first time a configuration initialises. They are pure noise in a plugin log. jOOQ reads these as
     * JVM system properties when it would log; set them before the first {@link DSL#using} call. An operator
     * who has set either property keeps their choice.
     */
    private static void silenceJooqBanner() {
        if (System.getProperty("org.jooq.no-logo") == null) {
            System.setProperty("org.jooq.no-logo", "true");
        }
        if (System.getProperty("org.jooq.no-tips") == null) {
            System.setProperty("org.jooq.no-tips", "true");
        }
    }
}
