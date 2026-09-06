package com.uxplima.uxmessentials.migration.convert.athelion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.convert.Convert;
import com.uxplima.uxmessentials.migration.convert.SourceDescriptor;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.migration.convert.athelion.map.AthelionWarpMapper;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WorldNameResolver;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The Athelion PlayerWarps {@link Convert}: the second of the three player-warp importers (docs/12-migration). Athelion
 * (the {@code dev.revivalo} PlayerWarps plugin) serialises every player-owned warp as a {@code ConfigurationSerializable}
 * into a single {@code plugins/PlayerWarps/data.yml}, so. Like the file-backed hologram sources rather than the JDBC
 * AxPlayerWarps source: it carries its own fixed data-file location instead of the shared {@code import.source-path}. It
 * parses each entry and maps it, with its ratings and blocked-players, into the shared {@code ImportedPlayerWarp} the
 * player-warp writer lands on the new schema.
 *
 * <p>The id is {@code athelionplayerwarps} (a source id is a single lowercase word. A hyphen is outside its charset), so
 * {@code /uxmess import athelionplayerwarps} resolves it. Stateless: one instance is registered for the lifetime of an
 * enabled module.
 */
@NullMarked
public final class AthelionPlayerWarpsConvert implements Convert {

    private static final SourceId ID = SourceId.of("athelionplayerwarps");
    private static final List<String> SURFACES = List.of("player-warps");

    private final Path dataFile;
    private final AthelionWarpMapper mapper;
    private final Logger log;

    /**
     * @param worlds the live world resolver (a stored world name the server does not know skips that warp)
     * @param dataFile the {@code plugins/PlayerWarps/data.yml} file Athelion serialises its warps into
     * @param log operator diagnostics for skipped files and malformed warps
     */
    public AthelionPlayerWarpsConvert(WorldNameResolver worlds, Path dataFile, Logger log) {
        this.dataFile = Objects.requireNonNull(dataFile, "dataFile");
        this.mapper = new AthelionWarpMapper(Objects.requireNonNull(worlds, "worlds"));
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public SourceId id() {
        return ID;
    }

    @Override
    public SourceDescriptor describe() {
        return new SourceDescriptor(
                ID, "Athelion PlayerWarps", "plugins/PlayerWarps/data.yml (Athelion's dev.revivalo plugin)", SURFACES);
    }

    @Override
    public boolean detect(Path sourcePath) {
        Objects.requireNonNull(sourcePath, "sourcePath");
        // Athelion brings its own fixed data file; the shared source-path argument does not apply.
        return Files.isRegularFile(dataFile);
    }

    @Override
    public ImportPlan plan(ImportOptions options) {
        Objects.requireNonNull(options, "options");
        return new AthelionPlayerWarpsPlan(dataFile, mapper, log);
    }
}
