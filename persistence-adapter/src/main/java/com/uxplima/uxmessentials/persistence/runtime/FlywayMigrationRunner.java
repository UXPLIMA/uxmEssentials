package com.uxplima.uxmessentials.persistence.runtime;

import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.jspecify.annotations.NullMarked;

/**
 * Applies the Flyway migrations on plugin enable, forward and idempotently.
 *
 * <p>The runner is given the migration classpath locations the enabled modules own (the V1 baseline at
 * {@code db/migration}, plus each context's own location as it lands) and the live {@link DataSource}.
 * It repairs then migrates: an already-migrated database is a no-op, a fresh one gets the baseline. The
 * repair realigns the schema-history checksums with the resolved scripts before applying anything, so a
 * cosmetic edit to an already-applied script (e.g. a reworded comment) self-heals on the next start
 * instead of failing validation and blocking enable on a server that ran the old script. The same scripts
 * run on every backend. The portable DDL means SQLite, MySQL and PostgreSQL converge on the same schema
 * (the backend-parity invariant). A migration failure is fatal and surfaced, never swallowed.
 */
@NullMarked
public final class FlywayMigrationRunner {

    private final DataSource dataSource;

    public FlywayMigrationRunner(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /**
     * Run every migration found under {@code locations}, creating the schema on a fresh database and
     * doing nothing on an already-current one. Locations are classpath-relative Flyway directories.
     */
    public void migrate(List<String> locations) {
        Objects.requireNonNull(locations, "locations");
        String[] resolved = locations.isEmpty() ? new String[] {"classpath:db/migration"} : prefix(locations);
        FluentConfiguration configuration = Flyway.configure(getClass().getClassLoader())
                .dataSource(dataSource)
                .locations(resolved)
                .baselineOnMigrate(true)
                .baselineVersion("0");
        try {
            Flyway flyway = configuration.load();
            // Realign recorded checksums with the resolved scripts before validating, so a cosmetic edit to
            // an already-applied migration (e.g. a reworded comment) does not brick a server that ran the
            // old script. Then apply forward; an up-to-date database is a no-op.
            flyway.repair();
            flyway.migrate();
        } catch (FlywayException cause) {
            throw new PersistenceException("database migration failed for locations " + locations, cause);
        }
    }

    private static String[] prefix(List<String> locations) {
        return locations.stream()
                .map(location -> location.startsWith("classpath:") ? location : "classpath:" + location)
                .toArray(String[]::new);
    }
}
