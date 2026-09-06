package com.uxplima.uxmessentials.migration.convert.decentholograms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.convert.Convert;
import com.uxplima.uxmessentials.migration.convert.SourceDescriptor;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.migration.convert.decentholograms.map.HologramMapper;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WorldNameResolver;
import org.jspecify.annotations.NullMarked;

/**
 * The DecentHolograms {@link Convert}. A file-backed source for server-wide holograms (docs/12-migration
 * §1.2). DecentHolograms stores one YAML file per hologram under {@code plugins/DecentHolograms/holograms/},
 * so unlike the player-data sources this one reads from a fixed plugin directory rather than the shared
 * {@code import.source-path}; it is handed that directory at construction, the same way the LiteBans source
 * carries its own JDBC location. It maps each file into the {@code holograms} context's {@code Hologram}
 * aggregate through its ACL mapper, resolving the stored world name against the live world list. Stateless:
 * one instance is registered for the lifetime of an enabled module.
 */
@NullMarked
public final class DecentHologramsConvert implements Convert {

    private static final SourceId ID = SourceId.of("decentholograms");

    private final Path hologramsDirectory;
    private final HologramMapper mapper;

    /**
     * @param worlds the live world resolver (a stored world name the server does not know skips that hologram)
     * @param hologramsDirectory the {@code plugins/DecentHolograms/holograms} directory the files live in
     */
    public DecentHologramsConvert(WorldNameResolver worlds, Path hologramsDirectory) {
        this.hologramsDirectory = Objects.requireNonNull(hologramsDirectory, "hologramsDirectory");
        this.mapper = new HologramMapper(worlds);
    }

    @Override
    public SourceId id() {
        return ID;
    }

    @Override
    public SourceDescriptor describe() {
        return new SourceDescriptor(
                ID, "DecentHolograms", "plugins/DecentHolograms with holograms/<name>.yml", List.of("holograms"));
    }

    @Override
    public boolean detect(Path sourcePath) {
        // DecentHolograms brings its own fixed directory; the shared source-path argument does not apply.
        return Files.isDirectory(hologramsDirectory);
    }

    @Override
    public ImportPlan plan(ImportOptions options) {
        Objects.requireNonNull(options, "options");
        return new DecentHologramsPlan(hologramsDirectory, mapper);
    }
}
