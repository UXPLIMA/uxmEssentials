package com.uxplima.uxmessentials.persistence.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.health.RepairableHealthCheck;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

/**
 * The persistence layer's lifecycle handle, opened once on plugin enable and closed on disable.
 *
 * <p>Opening it resolves the backend from config, builds the HikariCP data source (single-writer WAL for
 * the SQLite default, a pooled connection set for the network backends), runs the Flyway migrations
 * forward, and exposes the shared jOOQ {@link DSLContext} every repository binds to. Closing it shuts the
 * pool down. The contexts that own tables construct their repositories over {@link #dsl()}; this facade
 * does not know about specific tables. It is the single object bootstrap threads through the module
 * wiring so a context that declares migrations and repositories has a {@code DSLContext}.
 */
@NullMarked
public final class Persistence implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final DSLContext dsl;
    private final DatabaseBackend backend;

    private Persistence(HikariDataSource dataSource, DSLContext dsl, DatabaseBackend backend) {
        this.dataSource = dataSource;
        this.dsl = dsl;
        this.backend = backend;
    }

    /**
     * Open the persistence layer: build the data source, run migrations under {@code migrationLocations}
     * (or the V1 baseline at {@code db/migration} when empty), and create the shared context.
     *
     * @param config the (whole) plugin config; the {@code storage} subtree selects the backend
     * @param dataFolder the plugin data folder the embedded SQLite file lives under
     * @param migrationLocations the classpath Flyway locations the enabled modules contribute
     * @param log operator-facing diagnostics
     */
    public static Persistence open(ConfigStore config, Path dataFolder, List<String> migrationLocations, Logger log) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(migrationLocations, "migrationLocations");
        Objects.requireNonNull(log, "log");
        DatabaseSettings settings = new DatabaseSettings(config, dataFolder);
        DatabaseBackend backend = settings.backend();
        HikariDataSource dataSource = DataSourceFactory.create(settings);
        try {
            new FlywayMigrationRunner(dataSource).migrate(migrationLocations);
        } catch (RuntimeException cause) {
            dataSource.close();
            throw cause;
        }
        log.info("persistence opened on {} backend", backend.jdbcSubprotocol());
        return new Persistence(dataSource, DslContextProvider.create(dataSource, backend.dialect()), backend);
    }

    /** The shared jOOQ context every repository binds to. */
    public DSLContext dsl() {
        return dsl;
    }

    /** The active backend, for diagnostics and backend-specific repository decisions. */
    public DatabaseBackend backend() {
        return backend;
    }

    /** Build the cross-context integrity check without leaking jOOQ into the bootstrap adapter. */
    public RepairableHealthCheck integrityCheck(Path worldContainer) {
        return new PersistenceIntegrityHealthCheck(dsl, worldContainer);
    }

    /**
     * A lightweight liveness probe for the {@code /uxmess doctor} diagnostic: runs a trivial {@code SELECT 1}
     * to confirm a connection can be acquired and read the latest applied Flyway schema version from the
     * history table. Returns a {@link Probe} carrying both rather than throwing. A dead pool surfaces as a
     * {@code reachable = false} probe so the caller reports it instead of propagating. Must be called off the
     * tick thread (it acquires a connection).
     */
    public Probe probe() {
        try {
            Integer one = dsl.selectOne().fetchOne(0, Integer.class);
            boolean reachable = one != null && one == 1;
            return new Probe(reachable, backend.jdbcSubprotocol(), appliedVersion());
        } catch (RuntimeException unreachable) {
            return new Probe(false, backend.jdbcSubprotocol(), Optional.empty());
        }
    }

    private Optional<String> appliedVersion() {
        try {
            Record1<Object> row = dsl.select(DSL.max(DSL.field("version")))
                    .from(DSL.table("flyway_schema_history"))
                    .where(DSL.field("success").eq(true))
                    .fetchOne();
            Object version = row == null ? null : row.value1();
            return Optional.ofNullable(version).map(Object::toString);
        } catch (RuntimeException noHistoryTable) {
            // A backend without the Flyway history table (or an in-flight first migration) has no version to
            // report; the liveness half of the probe still stands on its own.
            return Optional.empty();
        }
    }

    /**
     * The result of a {@link #probe()}: whether a connection answered {@code SELECT 1}, the backend label, and
     * the latest applied Flyway version when the history table is readable.
     *
     * @param reachable whether the trivial query succeeded
     * @param backend the backend's JDBC subprotocol label, for the diagnostic line
     * @param schemaVersion the latest applied Flyway version, or empty when none is readable
     */
    public record Probe(boolean reachable, String backend, Optional<String> schemaVersion) {
        public Probe {
            Objects.requireNonNull(backend, "backend");
            Objects.requireNonNull(schemaVersion, "schemaVersion");
        }
    }

    /** Shut the connection pool down. Called on plugin disable, after the modules have stopped. */
    @Override
    public void close() {
        dataSource.close();
    }
}
