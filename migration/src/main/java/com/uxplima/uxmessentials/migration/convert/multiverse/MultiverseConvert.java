package com.uxplima.uxmessentials.migration.convert.multiverse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.convert.Convert;
import com.uxplima.uxmessentials.migration.convert.SourceDescriptor;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.migration.convert.multiverse.map.MultiverseWorldMapper;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The Multiverse-Core {@link Convert}, the world-registry importer (docs/12-migration). Multiverse keeps its whole
 * registry in one {@code plugins/Multiverse-Core/worlds.yml}, so, like the file-backed hologram and Athelion sources,
 * it carries its own fixed data-file location instead of the shared {@code import.source-path}.
 *
 * <p>The file is read from disk rather than from the running plugin. An operator importing away from Multiverse has
 * usually already stopped it, and a registry that could only be read while the competitor was enabled would make the
 * migration order matter; reading the file also means a dry run works on a server that never had Multiverse
 * installed, against a copied-in worlds.yml.
 *
 * <p>The id is {@code multiverse} (a source id is a single lowercase word. A hyphen is outside its charset), so
 * {@code /uxmess import multiverse} resolves it. Stateless: one instance is registered for the lifetime of an
 * enabled module.
 */
@NullMarked
public final class MultiverseConvert implements Convert {

    private static final SourceId ID = SourceId.of("multiverse");
    private static final List<String> SURFACES = List.of("worlds");

    private final Path worldsFile;
    private final MultiverseWorldMapper mapper;
    private final Logger log;

    /**
     * @param worldsFile the {@code plugins/Multiverse-Core/worlds.yml} Multiverse keeps its registry in
     * @param clock the clock stamping each imported world's creation time
     * @param log operator diagnostics for an unreadable file and for entries we cannot name
     */
    public MultiverseConvert(Path worldsFile, Clock clock, Logger log) {
        this.worldsFile = Objects.requireNonNull(worldsFile, "worldsFile");
        this.mapper = new MultiverseWorldMapper(Objects.requireNonNull(clock, "clock"));
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public SourceId id() {
        return ID;
    }

    @Override
    public SourceDescriptor describe() {
        return new SourceDescriptor(ID, "Multiverse-Core", "plugins/Multiverse-Core/worlds.yml", SURFACES);
    }

    @Override
    public boolean detect(Path sourcePath) {
        Objects.requireNonNull(sourcePath, "sourcePath");
        // Multiverse brings its own fixed registry file; the shared source-path argument does not apply.
        return Files.isRegularFile(worldsFile);
    }

    @Override
    public ImportPlan plan(ImportOptions options) {
        Objects.requireNonNull(options, "options");
        return new MultiversePlan(worldsFile, mapper, log);
    }
}
