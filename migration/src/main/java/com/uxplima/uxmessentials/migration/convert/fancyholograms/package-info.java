/**
 * The FancyHolograms import source (docs/12-migration §1.2): reads the single
 * {@code plugins/FancyHolograms/holograms.yml}, maps each entry's text lines into the {@code holograms}
 * context's {@code Hologram} aggregate, and streams them as {@code HologramRecord}s for the shared write
 * path. {@code parse/} confines the foreign YAML codec; the ACL translation reuses the DecentHolograms
 * {@code HologramMapper}, since both sources reduce to the same name/world/position/lines shape. Parsing
 * stays strictly in this source: no YAML node escapes to the importer.
 */
@NullMarked
package com.uxplima.uxmessentials.migration.convert.fancyholograms;

import org.jspecify.annotations.NullMarked;
