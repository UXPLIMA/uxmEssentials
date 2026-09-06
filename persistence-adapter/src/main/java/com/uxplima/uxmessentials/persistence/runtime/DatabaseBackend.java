package com.uxplima.uxmessentials.persistence.runtime;

import java.util.Locale;

import org.jooq.SQLDialect;
import org.jspecify.annotations.NullMarked;

/**
 * The relational backend the plugin runs against. SQLite is the default: single-node servers ship
 * with it and never touch a network database. MySQL/MariaDB and PostgreSQL are the network options an
 * operator selects in {@code modules.conf}; the same migrations and the same generated jOOQ DSL run
 * against every backend (the backend-parity invariant, {@code docs/01-architecture.md} §8).
 *
 * <p>Each constant carries its jOOQ {@link SQLDialect} so the {@code DSLContext} is rendered in the
 * dialect of the live connection, and the JDBC sub-protocol the {@code DataSourceFactory} builds the
 * URL from. SQLite additionally marks itself single-writer so the factory sizes the pool to one
 * connection and enables WAL ({@code docs/02-concurrency.md} §7.1).
 */
@NullMarked
public enum DatabaseBackend {

    /** Embedded file database; the default. Single-writer: WAL, write pool sized 1. */
    SQLITE("sqlite", "org.sqlite.JDBC", SQLDialect.SQLITE, true),

    /**
     * MySQL/MariaDB over the network. The MariaDB driver speaks both wire protocols, and the MYSQL
     * jOOQ dialect renders SQL valid on both. Crucially it emits {@code LIMIT}, whereas the MARIADB
     * dialect emits the standard {@code FETCH NEXT … ROWS ONLY} that MariaDB 10.6+ accepts but a real
     * MySQL 8 rejects. Using MYSQL keeps the one network backend correct against either server.
     */
    MARIADB("mariadb", "org.mariadb.jdbc.Driver", SQLDialect.MYSQL, false),

    /** PostgreSQL over the network. */
    POSTGRES("postgresql", "org.postgresql.Driver", SQLDialect.POSTGRES, false);

    private final String jdbcSubprotocol;
    private final String driverClassName;
    private final SQLDialect dialect;
    private final boolean singleWriter;

    DatabaseBackend(String jdbcSubprotocol, String driverClassName, SQLDialect dialect, boolean singleWriter) {
        this.jdbcSubprotocol = jdbcSubprotocol;
        this.driverClassName = driverClassName;
        this.dialect = dialect;
        this.singleWriter = singleWriter;
    }

    /** The JDBC sub-protocol, e.g. {@code sqlite} in {@code jdbc:sqlite:...}. */
    public String jdbcSubprotocol() {
        return jdbcSubprotocol;
    }

    /**
     * The JDBC driver class to register explicitly. The drivers are supplied by the Paper library
     * loader into the plugin's isolated classloader, which {@code DriverManager} (loaded by the system
     * classloader) does not scan; naming the class so HikariCP loads it directly is what makes a
     * network backend connect instead of failing with "No suitable driver".
     */
    public String driverClassName() {
        return driverClassName;
    }

    /** The jOOQ dialect the {@code DSLContext} renders SQL in for this backend. */
    public SQLDialect dialect() {
        return dialect;
    }

    /** True when the engine permits only one concurrent writer (SQLite). */
    public boolean isSingleWriter() {
        return singleWriter;
    }

    /**
     * Resolve a backend from a config token. Recognises {@code sqlite}, {@code mysql}/{@code mariadb},
     * and {@code postgres}/{@code postgresql}; anything else falls back to the SQLite default rather
     * than failing, so a typo degrades to the safe embedded backend instead of refusing to enable.
     */
    public static DatabaseBackend fromToken(String token) {
        return switch (token.trim().toLowerCase(Locale.ROOT)) {
            case "mysql", "mariadb" -> MARIADB;
            case "postgres", "postgresql", "psql" -> POSTGRES;
            default -> SQLITE;
        };
    }
}
