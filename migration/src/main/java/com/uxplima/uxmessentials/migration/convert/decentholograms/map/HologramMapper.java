package com.uxplima.uxmessentials.migration.convert.decentholograms.map;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.migration.convert.decentholograms.parse.DhHologram;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WorldNameResolver;
import com.uxplima.uxmessentials.migration.convert.map.ImportedHologram;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * Translates a parsed DecentHolograms hologram into a domain {@link Hologram} (docs/12-migration §5).
 * DecentHolograms stores far more per hologram than uxmEssentials models on a single hologram, pages,
 * per-line flags/offsets, click actions, display/update ranges, a facing angle. The importer keeps the
 * portable core: the name, the location, and the first page's text lines, with uxmEssentials' default
 * appearance, everyone-visibility, and static refresh: the same shape {@code /hologram create} produces.
 * A hologram whose world the server does not know, whose name is invalid, or that has no non-blank line
 * maps to {@link Optional#empty()} and the caller counts it a skipped record (docs/12-migration §4).
 */
@NullMarked
public final class HologramMapper {

    private final WorldNameResolver worlds;

    public HologramMapper(WorldNameResolver worlds) {
        this.worlds = Objects.requireNonNull(worlds, "worlds");
    }

    public Optional<ImportedHologram> map(DhHologram src) {
        Objects.requireNonNull(src, "src");
        return worlds.resolve(src.world()).flatMap(world -> build(world, src));
    }

    private Optional<ImportedHologram> build(WorldRef world, DhHologram src) {
        Optional<HologramName> name = name(src.name());
        if (name.isEmpty()) {
            return Optional.empty();
        }
        List<HologramLine> lines = lines(src.lines());
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        Position location = Position.of(world, src.x(), src.y(), src.z());
        return Optional.of(new ImportedHologram(Hologram.create(name.get(), location, lines, Instant.EPOCH)));
    }

    /** The name, or empty when DecentHolograms named the file something the domain rejects (blank/overlong). */
    private static Optional<HologramName> name(String raw) {
        try {
            return Optional.of(HologramName.of(raw));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    /** The non-blank lines that fit the column width; a blank DecentHolograms spacer carries no text and is dropped. */
    private static List<HologramLine> lines(List<String> raw) {
        List<HologramLine> lines = new ArrayList<>();
        for (String line : raw) {
            String stripped = line.strip();
            if (!stripped.isEmpty() && stripped.length() <= HologramLine.MAX_LENGTH) {
                lines.add(new HologramLine(stripped));
            }
        }
        return lines;
    }
}
