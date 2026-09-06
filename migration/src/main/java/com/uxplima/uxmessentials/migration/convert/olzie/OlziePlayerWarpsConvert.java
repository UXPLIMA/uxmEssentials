package com.uxplima.uxmessentials.migration.convert.olzie;

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
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WorldNameResolver;
import com.uxplima.uxmessentials.migration.convert.jdbc.JdbcConnection;
import com.uxplima.uxmessentials.migration.convert.jdbc.JdbcSource;
import com.uxplima.uxmessentials.migration.convert.olzie.map.OlzieWarpMapper;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The Olzie PlayerWarps {@link Convert}. The last of the three player-warp importers (docs/12-migration), and the
 * richest: it reads the {@code playerwarps_*} tables over JDBC and maps each warp, with its rating / whitelist /
 * manager / ban / favourite / visit side rows, into the shared {@code ImportedPlayerWarp} the player-warp writer lands
 * on the new schema. Like the AxPlayerWarps and LiteBans sources it is a database source, not a path source: its data
 * location is the configured database (or the discovered SQLite file), so {@code plan} reads from
 * {@link OlziePlayerWarpsConfig} and ignores {@code options.sourcePath()}.
 *
 * <p>The id is {@code olzieplayerwarps} (a source id is a single lowercase word. A hyphen is outside its charset), so
 * {@code /uxmess import olzieplayerwarps} resolves it. Stateless beyond its injected config, world resolver, and logger.
 */
@NullMarked
public final class OlziePlayerWarpsConvert implements Convert {

    private static final SourceId ID = SourceId.of("olzieplayerwarps");
    private static final List<String> SURFACES = List.of("player-warps");

    private final OlziePlayerWarpsConfig config;
    private final OlzieWarpMapper mapper;
    private final Logger log;

    public OlziePlayerWarpsConvert(OlziePlayerWarpsConfig config, WorldNameResolver worlds, Logger log) {
        this.config = Objects.requireNonNull(config, "config");
        this.mapper = new OlzieWarpMapper(Objects.requireNonNull(worlds, "worlds"));
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public SourceId id() {
        return ID;
    }

    @Override
    public SourceDescriptor describe() {
        return new SourceDescriptor(
                ID,
                "Olzie PlayerWarps",
                "an Olzie PlayerWarps database (JDBC URL, or a SQLite file under plugins/PlayerWarps/data/)",
                SURFACES);
    }

    @Override
    public boolean detect(Path sourcePath) {
        Objects.requireNonNull(sourcePath, "sourcePath");
        // A DB source has no on-disk tree to probe; presence is whether a connection resolves and opens.
        return config.connection().map(JdbcSource::canOpen).orElse(false);
    }

    @Override
    public ImportPlan plan(ImportOptions options) {
        Objects.requireNonNull(options, "options");
        Optional<JdbcConnection> connection = config.connection();
        if (connection.isEmpty()) {
            log.warn("Olzie PlayerWarps import requested but no jdbc-url is configured and no SQLite file was found "
                    + "under plugins/PlayerWarps/data/; nothing to read");
            return EmptyPlan.INSTANCE;
        }
        return new OlziePlayerWarpsPlan(connection.orElseThrow(), mapper, log);
    }

    /** The plan returned when no Olzie database can be resolved: an empty, no-op stream. */
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
