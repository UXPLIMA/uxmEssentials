package com.uxplima.uxmessentials.migration.convert.fancyholograms;

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
 * The FancyHolograms {@link Convert}. A file-backed source for server-wide holograms (docs/12-migration
 * §1.2). Where DecentHolograms writes one file per hologram, FancyHolograms keeps the whole set in a single
 * {@code plugins/FancyHolograms/holograms.yml}; like the DecentHolograms and LiteBans sources it carries its
 * own fixed location rather than the shared {@code import.source-path}. It maps each entry into the {@code
 * holograms} context's {@code Hologram} aggregate through the shared hologram ACL mapper, resolving the
 * stored world name against the live world list. Stateless: one instance is registered for the lifetime of
 * an enabled module.
 */
@NullMarked
public final class FancyHologramsConvert implements Convert {

    private static final SourceId ID = SourceId.of("fancyholograms");

    private final Path hologramsFile;
    private final HologramMapper mapper;

    /**
     * @param worlds the live world resolver (a stored world name the server does not know skips that hologram)
     * @param hologramsFile the {@code plugins/FancyHolograms/holograms.yml} file the holograms live in
     */
    public FancyHologramsConvert(WorldNameResolver worlds, Path hologramsFile) {
        this.hologramsFile = Objects.requireNonNull(hologramsFile, "hologramsFile");
        this.mapper = new HologramMapper(worlds);
    }

    @Override
    public SourceId id() {
        return ID;
    }

    @Override
    public SourceDescriptor describe() {
        return new SourceDescriptor(ID, "FancyHolograms", "plugins/FancyHolograms/holograms.yml", List.of("holograms"));
    }

    @Override
    public boolean detect(Path sourcePath) {
        // FancyHolograms brings its own fixed file; the shared source-path argument does not apply.
        return Files.isRegularFile(hologramsFile);
    }

    @Override
    public ImportPlan plan(ImportOptions options) {
        Objects.requireNonNull(options, "options");
        return new FancyHologramsPlan(hologramsFile, mapper);
    }
}
