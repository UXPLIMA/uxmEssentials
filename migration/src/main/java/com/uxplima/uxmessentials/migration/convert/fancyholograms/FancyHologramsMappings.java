package com.uxplima.uxmessentials.migration.convert.fancyholograms;

import java.util.List;

import com.uxplima.uxmessentials.migration.MappingRow;
import org.jspecify.annotations.NullMarked;

/**
 * The FancyHolograms source's {@code SupportedMappings} rows. Its claim of what it migrates
 * (docs/12-migration §5.6). FancyHolograms holograms are server-wide display objects, not player data, so
 * the source contributes a single row: each entry under {@code holograms.yml}'s {@code holograms} section
 * maps to one {@code Hologram}, keyed on its name. The deliberate gaps. Item/block holograms (no text),
 * per-line offsets, billboard/scale/shadow display flags. Are not modelled on the imported hologram and
 * carry no row.
 */
@NullMarked
public final class FancyHologramsMappings {

    private static final List<MappingRow> ROWS = List.of(new MappingRow(
            "holograms.yml#holograms.<name>", "Hologram", "holograms", "HologramMapper", "hologramName"));

    private FancyHologramsMappings() {}

    /** The FancyHolograms mapping rows, in doc order. */
    public static List<MappingRow> rows() {
        return ROWS;
    }
}
