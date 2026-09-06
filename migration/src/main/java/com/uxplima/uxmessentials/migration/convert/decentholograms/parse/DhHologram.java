package com.uxplima.uxmessentials.migration.convert.decentholograms.parse;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * One parsed DecentHolograms hologram file, in the foreign plugin's own terms before any mapping: the
 * file-name stem as the hologram's name, the raw world name and coordinates lifted from the {@code
 * location} string, and the ordered raw line contents of the first page. No domain type appears here
 * world resolution and the {@code Hologram} aggregate are the {@code map/} layer's job (docs/12-migration
 * §2). DecentHolograms supports multiple pages, flags, per-line height/offsets and click actions; an
 * imported hologram keeps only the first page's text, which is the single-page hologram every other
 * source produces.
 *
 * @param name the hologram name (the {@code <name>.yml} file stem)
 * @param world the raw world name from the {@code location} string
 * @param x world x coordinate
 * @param y world y coordinate
 * @param z world z coordinate
 * @param lines the first page's raw line contents, in order
 */
@NullMarked
public record DhHologram(String name, String world, double x, double y, double z, List<String> lines) {

    public DhHologram {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(world, "world");
        lines = List.copyOf(lines);
    }
}
