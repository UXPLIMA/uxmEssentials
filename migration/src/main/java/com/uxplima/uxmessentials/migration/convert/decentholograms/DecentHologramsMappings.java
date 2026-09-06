package com.uxplima.uxmessentials.migration.convert.decentholograms;

import java.util.List;

import com.uxplima.uxmessentials.migration.MappingRow;
import org.jspecify.annotations.NullMarked;

/**
 * The DecentHolograms source's {@code SupportedMappings} rows. Its claim of what it migrates
 * (docs/12-migration §5). DecentHolograms holograms are server-wide display objects, not player data, so
 * the source contributes a single row: each {@code holograms/<name>.yml} file's first page maps to one
 * {@code Hologram}, keyed on its name. The deliberate gaps. Multi-page holograms (only the first page is
 * kept), per-line flags/offsets, click actions, item/icon holograms. Are documented as not-migrated and
 * carry no row.
 */
@NullMarked
public final class DecentHologramsMappings {

    private static final List<MappingRow> ROWS =
            List.of(new MappingRow("holograms/<name>.yml", "Hologram", "holograms", "HologramMapper", "hologramName"));

    private DecentHologramsMappings() {}

    /** The DecentHolograms mapping rows, in doc order. */
    public static List<MappingRow> rows() {
        return ROWS;
    }
}
