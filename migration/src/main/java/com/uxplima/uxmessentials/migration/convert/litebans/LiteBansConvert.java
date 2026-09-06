package com.uxplima.uxmessentials.migration.convert.litebans;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.Convert;
import com.uxplima.uxmessentials.migration.convert.SourceDescriptor;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.migration.convert.jdbc.JdbcConnection;
import com.uxplima.uxmessentials.migration.convert.jdbc.JdbcSource;
import com.uxplima.uxmessentials.migration.convert.litebans.parse.LiteBansTables;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The LiteBans {@link Convert}: the importer's first JDBC-backed source (docs/12-migration §1.1, §5.4). It
 * reads the {@code litebans_bans}/{@code litebans_mutes}/{@code litebans_warnings} tables over JDBC and maps
 * them into the moderation context's ban / IP-ban / mute / warning aggregates through its own ACL mappers,
 * draining into the same writer every other source uses. Like the live economy sources it is not a path
 * source: its data location is the configured database (or the discovered H2 file), so {@code plan} reads
 * from {@link LiteBansConfig} and ignores {@code options.sourcePath()}.
 *
 * <p>Stateless beyond its injected config and logger: one instance is registered for the lifetime of an
 * enabled module.
 */
@NullMarked
public final class LiteBansConvert implements Convert {

    private static final SourceId ID = SourceId.of("litebans");
    private static final List<String> SURFACES = List.of("bans", "ip-bans", "mutes", "warns");

    private final LiteBansConfig config;
    private final Logger log;

    public LiteBansConvert(LiteBansConfig config, Logger log) {
        this.config = Objects.requireNonNull(config, "config");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public SourceId id() {
        return ID;
    }

    @Override
    public SourceDescriptor describe() {
        return new SourceDescriptor(
                ID, "LiteBans", "a LiteBans database (JDBC URL, or an H2 file under plugins/LiteBans/)", SURFACES);
    }

    @Override
    public boolean detect(Path sourcePath) {
        Objects.requireNonNull(sourcePath, "sourcePath");
        // A DB source has no on-disk tree to probe; presence is whether a connection resolves and opens, or
        // an H2 file is at least present under plugins/LiteBans/ even if the engine cannot read it.
        Optional<JdbcConnection> connection = config.connection();
        if (connection.isPresent() && JdbcSource.canOpen(connection.orElseThrow())) {
            return true;
        }
        return config.h2DataFile().isPresent();
    }

    @Override
    public ImportPlan plan(ImportOptions options) {
        Objects.requireNonNull(options, "options");
        Optional<JdbcConnection> connection = config.connection();
        if (connection.isEmpty()) {
            log.warn("LiteBans import requested but no jdbc-url is configured and no H2 file was found under "
                    + "plugins/LiteBans/; nothing to read");
            return EmptyPlan.INSTANCE;
        }
        LiteBansTables tables = new LiteBansTables(config.tablePrefix());
        return new LiteBansPlan(connection.orElseThrow(), tables, log);
    }

    /** The plan returned when no LiteBans database can be resolved: an empty, no-op stream. */
    private enum EmptyPlan implements ImportPlan {
        INSTANCE;

        @Override
        public Stream<ImportRecord> records() {
            return Stream.empty();
        }

        @Override
        public void close() {
            // Nothing was opened.
        }
    }
}
